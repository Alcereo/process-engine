package ru.alcereo.processdsl.task;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.testkit.javadsl.TestKit;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.ConfigFactory;
import lombok.val;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.mockserver.MockServer;
import org.mockserver.mockserver.MockServerBuilder;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

import static org.codehaus.groovy.runtime.NioGroovyMethods.deleteDir;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class RestSyncTaskActorTest {

    private ActorSystem system;

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
    public void restSuccessfulRequestTest() throws InterruptedException {

        TestKit probe = new TestKit(system);
        ActorRef taskActor = probe.childActorOf(RestSyncTaskActor.props(UUID.randomUUID(), new OkHttpClient()), "task");

        System.out.println("----    Start append context   -----");

        taskActor.tell(
                new PersistFSMTask.AppendToContextCmd(
                        ImmutableMap.of("address",TEST_ADDRESS)
                ),
                probe.getRef());
        probe.expectMsgClass(PersistFSMTask.CmdSuccess.class);

        System.out.println("----   Append context finish   -----");
        System.out.println("----       Start execution     -----");

        taskActor.tell(
                new PersistFSMTask.StartExecutingCmd(),
                probe.getRef()
        );
        probe.expectMsgClass(PersistFSMTask.CmdSuccess.class);

        System.out.println("---- Finish command execution -----");

        probe.expectMsgClass(PersistFSMTask.SuccessExecutedEvt.class);

        System.out.println("----  Get succesfull message  -----");

        taskActor.tell(
                new PersistFSMTask.GetStateDataQuery(),
                probe.getRef()
        );
        PersistFSMTask.TaskDataState taskDataState = probe.expectMsgClass(PersistFSMTask.TaskDataState.class);

        System.out.println("----  Get data with context   -----");

        assertEquals(
                TEST_RESPONSE,
                taskDataState.getProperties().get("http-response")
        );

    }

    @Test
    public void restRequestExceptionTest() throws InterruptedException, IOException {

        TestKit probe = new TestKit(system);

        Call call = mock(Call.class);
        when(call.execute()).thenThrow(new IOException("Test error!"));

        OkHttpClient client = mock(OkHttpClient.class);
        when(client.newCall(any())).thenReturn(call);

        ActorRef taskActor = probe.childActorOf(RestSyncTaskActor.props(UUID.randomUUID(), client), "task");

        System.out.println("----    Start append context   -----");

        taskActor.tell(
                new PersistFSMTask.AppendToContextCmd(
                        ImmutableMap.of("address","http://"+HOST+":"+PORT)
                ),
                probe.getRef());
        probe.expectMsgClass(PersistFSMTask.CmdSuccess.class);

        System.out.println("---- V Append context finish   -----");
        System.out.println("----       Start execution     -----");

        taskActor.tell(
                new PersistFSMTask.StartExecutingCmd(),
                probe.getRef()
        );
        probe.expectMsgClass(PersistFSMTask.CmdSuccess.class);

        System.out.println("---- V Finish command execution -----");
        System.out.println("----    Get error message     -----");

        PersistFSMTask.ExecutedWithErrorsEvt errorMsg = probe.expectMsgClass(PersistFSMTask.ExecutedWithErrorsEvt.class);

        assertTrue(
                "Исключение не заполнено",
                errorMsg.getError()!=null
        );

        System.out.println("---- V Error message checked -----");
        System.out.println("----  Get state for checking -----");

        taskActor.tell(
                new PersistFSMTask.GetTaskStateQuery(),
                probe.getRef()
        );
        PersistFSMTask.TaskState taskState = probe.expectMsgClass(PersistFSMTask.TaskState.class);

        assertEquals(
                PersistFSMTask.TaskState.ERROR_FINISHED,
                taskState
        );

        System.out.println("----    V State checked      -----");

    }

}
