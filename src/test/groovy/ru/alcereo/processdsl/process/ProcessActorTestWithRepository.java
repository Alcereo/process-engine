package ru.alcereo.processdsl.process;

import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.Status;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;
import lombok.val;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import ru.alcereo.processdsl.domain.task.AbstractTask;
import ru.alcereo.processdsl.domain.BusinessProcess;
import scala.concurrent.duration.FiniteDuration;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.codehaus.groovy.runtime.NioGroovyMethods.deleteDir;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class ProcessActorTestWithRepository {

    ActorSystem system;

    @Before
    public void setUp() throws Exception {
        val config = ConfigFactory.load("test-config");
        system = ActorSystem.create("test-config", config);
    }

    @After
    public void tearDown() throws Exception {
        system.terminate();
        Path path = Paths.get("build", "persistent");
        deleteDir(path);
    }

    @Test
    public void testProcessStart(){

        val repository = system.actorOf(Props.create(ProcessInMemoryRepository.class), "repository");
        val probe = new TestKit(system);

        BusinessProcess process = mock(BusinessProcess.class);
        when(process.getIdentifier()).thenReturn(UUID.randomUUID());

        repository.tell(new ProcessRepositoryAbstractActor.AddProcess(process), probe.getRef());
        probe.expectMsgClass(Status.Success.class);

        repository.tell(new ProcessRepositoryAbstractActor.GetProcessByUID(process.getIdentifier()), probe.getRef());
        probe.expectMsgClass(BusinessProcess.class);

//        ----

        val processApiActor = system.actorOf(ProcessActor.props((system1, actorName) -> repository), "process-api");

        AbstractTask task = mock(AbstractTask.class);

        processApiActor.tell(new ProcessActor.SetTasksToProcessCmd(process.getIdentifier(), task), probe.getRef());
        probe.expectMsgClass(ProcessActor.SuccessCommand.class);

    }

    @Test
    public void testTimeoutExceptionOnRepo(){

        val repository = new TestKit(system).getRef();
        val probe = new TestKit(system);

        BusinessProcess process = mock(BusinessProcess.class);
        when(process.getIdentifier()).thenReturn(UUID.randomUUID());

//        ----

        val processApiActor = system.actorOf(ProcessActor.props((system1, actorName) -> repository), "process-api");

        AbstractTask task = mock(AbstractTask.class);

        processApiActor.tell(new ProcessActor.SetTasksToProcessCmd(process.getIdentifier(), task), probe.getRef());
        ProcessActor.CommandException commandException = probe.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                ProcessActor.CommandException.class
        );

        assertTrue(
                "Исключение не заполнено",
                commandException.getException() != null
        );

    }


    @Test
    public void processNotFoundException(){

        val repository = system.actorOf(Props.create(ProcessInMemoryRepository.class), "repository");
        val probe = new TestKit(system);

        BusinessProcess process = mock(BusinessProcess.class);
        when(process.getIdentifier()).thenReturn(UUID.randomUUID());

//        ---

        val processApiActor = system.actorOf(ProcessActor.props((system1, actorName) -> repository), "process-api");

        AbstractTask task = mock(AbstractTask.class);

        processApiActor.tell(new ProcessActor.SetTasksToProcessCmd(process.getIdentifier(), task), probe.getRef());
        ProcessActor.CommandException commandException = probe.expectMsgClass(
                FiniteDuration.apply(3, TimeUnit.SECONDS),
                ProcessActor.CommandException.class
        );

        assertTrue(
                "Исключение не заполнено",
                commandException.getException() != null
        );

    }

}