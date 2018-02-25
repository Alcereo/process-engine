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
import ru.alcereo.processdsl.domain.Task;
import ru.alcereo.processdsl.domain.TaskActorType;
import ru.alcereo.processdsl.task.PersistFSMTask;
import ru.alcereo.processdsl.task.RestSyncTask;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
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

        System.out.println("------------------ Create process");
        UUID processUuid = UUID.randomUUID();
        processApiActor.tell(new ProcessActor.CreateNewProcessCmd(processUuid), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);


        System.out.println("------------------ Add first task to process");
        UUID firstTaskUuid = UUID.randomUUID();
        Task firstTask = new Task(
                firstTaskUuid,
                ImmutableMap.of("address", TEST_ADDRESS),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                () -> RestSyncTask.props(firstTaskUuid,new OkHttpClient())
        );
        processApiActor.tell(new ProcessActor.AddLastTaskCmd(processUuid, firstTask), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);


        System.out.println("------------------ Add second task to process");
        UUID secondTaskUuid = UUID.randomUUID();
        Task secondTask = new Task(
                secondTaskUuid,
                ImmutableMap.of("text-to-print", "Some text to print"),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                TestPrintTaskActor.getType(secondTaskUuid,"text-to-print")
        );
        processApiActor.tell(new ProcessActor.AddLastTaskCmd(processUuid, secondTask), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);


        System.out.println("------------------ Start execution process");
        processApiActor.tell(new ProcessActor.StartProcessCmd(processUuid), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);


        Thread.sleep(5000);

//        BusinessProcess process = mock(BusinessProcess.class);
//        when(process.getIdentifier()).thenReturn(UUID.randomUUID());
//
//        repository.tell(new ProcessRepositoryAbstractActor.AddProcess(process), probe.getRef());
//        probe.expectMsgClass(Status.Success.class);
//
//        repository.tell(new ProcessRepositoryAbstractActor.GetProcessByUID(process.getIdentifier()), probe.getRef());
//        probe.expectMsgClass(BusinessProcess.class);

//        ----


//        Task task = mock(Task.class);
//
//        processApiActor.tell(new ProcessActor.AddLastTaskCmd(process.getIdentifier(), task), probe.getRef());
//        probe.expectMsgClass(ProcessActor.SuccessCommand.class);

    }

}