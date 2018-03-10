package ru.alcereo.processdsl.process;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.javadsl.TestKit;
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
import ru.alcereo.processdsl.domain.task.AbstractTask;
import ru.alcereo.processdsl.domain.task.OneDirectionTask;
import ru.alcereo.processdsl.domain.task.ProcessSuccessResultTask;
import ru.alcereo.processdsl.domain.task.PropertiesExchangeData;
import ru.alcereo.processdsl.task.RestSyncTaskActor;
import ru.alcereo.processdsl.task.TestPrintTaskActor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.UUID;

import static org.codehaus.groovy.runtime.NioGroovyMethods.deleteDir;


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


    @Test
    @SuppressWarnings("unchecked")
    public void testProcessStart() throws InterruptedException {

        val processApiActor = system.actorOf(
                ProcessActor.props(
                        (system1, actorName) ->
                                system1.actorOf(
                                        Props.create(ProcessInMemoryRepository.class),
                                        actorName)
                ), "process-api");
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
                .nextTask(
                        successFinishTask
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
                ).nextTask(
                        secondTask
                ).type(
                        () -> RestSyncTaskActor.props(firstTaskUuid,new OkHttpClient())
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