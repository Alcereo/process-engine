package ru.alcereo.processdsl.process;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.javadsl.TestKit;
import akka.util.Timeout;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import lombok.val;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import ru.alcereo.processdsl.domain.BusinessProcess;
import ru.alcereo.processdsl.domain.TaskActorType;
import ru.alcereo.processdsl.domain.task.AbstractTask;
import ru.alcereo.processdsl.domain.task.OneDirectionTask;
import ru.alcereo.processdsl.domain.task.ProcessSuccessResultTask;
import ru.alcereo.processdsl.domain.task.PropertiesExchangeData;
import ru.alcereo.processdsl.task.PersistFSMTask;
import ru.alcereo.processdsl.task.RestSyncTask;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.codehaus.groovy.runtime.NioGroovyMethods.deleteDir;
import static ru.alcereo.processdsl.Utils.failure;

public class ProcessActorTaskExecutingTest {

    ActorSystem system;

    private MockServer mockServer;
    private final String HOST = "127.0.0.1";
    private final Integer PORT = 8081;
    private final String TEST_RESPONSE = "{\"name\":\"Ivan\", \"age\":12}";
    private final String TEST_ADDRESS = "http://"+HOST+":"+PORT;

    @Before
    public void setUp() throws Exception {
        val config = ConfigFactory.load("test-config");
        system = ActorSystem.create("test-config", config);

        mockServer = new MockServerBuilder()
                .withHTTPPort(PORT)
                .build();

        new MockServerClient(HOST, PORT)
                .when(
                        HttpRequest.request().withMethod("GET")
                ).respond(
                HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(TEST_RESPONSE)
        );

    }

    @After
    public void tearDown() throws Exception {
        system.terminate();
        Path path = Paths.get("build", "persistent");
        deleteDir(path);

        mockServer.stop().get();
    }


    private static class TestPrintTaskActor extends PersistFSMTask{

        private final String propertyToPrint;

        public TestPrintTaskActor(UUID taskIdentifier, String propertyToPrint) {
            super(taskIdentifier);
            this.propertyToPrint = propertyToPrint;
        }

        public static TaskActorType getType(UUID taskIdentifier, String propertyToPrint){
            return () -> Props.create(
                    TestPrintTaskActor.class,
                    () -> new TestPrintTaskActor(taskIdentifier, propertyToPrint)
            );
        }

        @Override
        public void handleExecution(TaskDataState taskStateData) {

            ActorRef initSender = getSender();

            System.out.println("=============================================");
            System.out.println("=================PRINT-ACTOR=================");
            System.out.println();

            System.out.println(taskStateData.getProperties().get(propertyToPrint));

            System.out.println();
            System.out.println("=============================================");
            System.out.println("=============================================");

            ExecutionContext ds = getContext().dispatcher();

            Future<Object> sendSuccessF = Patterns.ask(
                    getSelf(),
                    new SuccessFinishExecutingCmd(),
                    Timeout.apply(3, TimeUnit.SECONDS)
            );

            sendSuccessF.onFailure(
                    failure(throwable -> {
                        log().error(throwable, "Error execution task!!");

                        Patterns.ask(
                                getSelf(),
                                new ErrorExecutingCmd(throwable),
                                Timeout.apply(5, TimeUnit.SECONDS)
                        ).onFailure(
                                failure(throwable1 -> {
                                    throw new RuntimeException("Error execution!!");
                                }), ds
                        );
                    }), ds);

        }
    }


    @Test
    @SuppressWarnings("unchecked")
    public void testProcessStart() throws InterruptedException {

        val repository = Props.create(ProcessInMemoryRepository.class);
        val processApiActor = system.actorOf(ProcessActor.props(repository), "process-api");
        val probe = new TestKit(system);
        val observerProbe = new TestKit(system);

//        Create tasks

        UUID firstTaskUuid = UUID.randomUUID();
        UUID secondTaskUuid = UUID.randomUUID();

        String processAddressPropName = "request-address";
        String processResponsePropName = "request-result";

        AbstractTask successFinishTask = ProcessSuccessResultTask.builder()
                .identifier(UUID.randomUUID())
                .build();

        AbstractTask secondTask = OneDirectionTask.builder()
                .identifier(secondTaskUuid)
                .properties(new HashMap<>())
                .taskList(
                        Collections.singletonList(successFinishTask)
                ).propertiesExchangeData(
                        PropertiesExchangeData.builder()
                                .addInnerPropsFromContext("text-to-print", processResponsePropName)
                                .build()
                ).type(
                        TestPrintTaskActor.getType(secondTaskUuid,"text-to-print")
                ).build();

        AbstractTask firstTask = OneDirectionTask.builder()
                .identifier(firstTaskUuid)
                .properties(new HashMap<>())
                .propertiesExchangeData(
                        PropertiesExchangeData.builder()
                                .addInnerPropsFromContext("address", processAddressPropName)
                                .addOuterPropsToContext("http-response", processResponsePropName)
                                .build()
                ).taskList(
                        Collections.singletonList(secondTask)
                ).type(
                        () -> RestSyncTask.props(firstTaskUuid,new OkHttpClient())
                ).build();


        System.out.println("------------------ Add observer");
        processApiActor.tell(new ProcessActor.AddObserverMsg(observerProbe.getRef()), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessAdded.class);



        System.out.println("------------------ Create process");
        UUID processUuid = UUID.randomUUID();

        ImmutableMap<String, Object> processProps = ImmutableMap.of(processAddressPropName, TEST_ADDRESS);
        processApiActor.tell(
                new ProcessActor.CreateNewProcessCmd(processUuid, processProps),
                probe.getRef()
        );
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);
        observerProbe.expectMsgClass(BusinessProcess.ProcessCreatedEvt.class);


        System.out.println("------------------ Add tasks to process");
        processApiActor.tell(new ProcessActor.SetTasksToProcessCmd(processUuid, firstTask), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);
        observerProbe.expectMsgClass(BusinessProcess.LastTaskAddedEvt.class);


        System.out.println("------------------ Start execution process");
        processApiActor.tell(new ProcessActor.StartProcessCmd(processUuid), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);
        observerProbe.expectMsgClass(BusinessProcess.ProcessStartedEvt.class);


        System.out.println("------------------ Wait for finish");
        observerProbe.expectMsgClass(BusinessProcess.ProcessFinishedEvt.class);

    }

}