package ru.alcereo.processdsl.task

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before

import java.nio.file.Paths

import static ru.alcereo.processdsl.task.PersistTaskActor.*

/**
 * Created by alcereo on 03.01.18.
 */
class PersistTaskActorTest extends GroovyTestCase{

    ActorSystem system

    @Before
    void setUp() throws Exception {
        def config = ConfigFactory.load("test-config")
        system = ActorSystem.create("test-config", config)

    }

    @After
    void tearDown() throws Exception {
        system.terminate()
        Paths.get("build","persistent").deleteDir() // Очистка хранилища

    }


    void testPersisActor(){
        def taskActor = system.actorOf(
                props("actor-persist-id"),
                "task"
        )

        def probe = new TestKit(system)

        taskActor.tell(new GetStateMsg(), probe.getRef())
        def state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.Stage.NEW,
                state.stage
        )

    }

    void testPrepareTestActor() {
        def taskActor = system.actorOf(
                props("actor-persist-id"),
                "task"
        )

        def probe = new TestKit(system)

        taskActor.tell(new GetStateMsg(), probe.getRef())
        def state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.Stage.NEW,
                state.stage
        )

        def preparedProps = [
                "text": "This text will printed"
        ]

        taskActor.tell(new PrepareCmd(preparedProps), probe.getRef())
        def preparedEvt = probe.expectMsgClass(PreparedEvt.class)

        assertEquals(
                "This text will printed",
                preparedEvt.textToPrint
        )

        taskActor.tell(new GetStateMsg(), probe.getRef())
        state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.Stage.PREPARED,
                state.stage
        )


    }


    void testStateReceiveOnExit() {
        def taskActor = system.actorOf(
                props("actor-persist-id"),
                "task"
        )

        def probe = new TestKit(system)

        taskActor.tell(new GetStateMsg(), probe.getRef())
        def state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.Stage.NEW,
                state.stage
        )

        def preparedProps = [
                "text": "This text will printed"
        ]

        taskActor.tell(new PrepareCmd(preparedProps), probe.getRef())
        def preparedEvt = probe.expectMsgClass(PreparedEvt.class)

        assertEquals(
                "This text will printed",
                preparedEvt.textToPrint
        )

        taskActor.tell(new GetStateMsg(), probe.getRef())
        state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.Stage.PREPARED,
                state.stage
        )

        taskActor.tell(PoisonPill.instance, ActorRef.noSender())

        Thread.sleep(100)

        taskActor = system.actorOf(
                props("actor-persist-id"),
                "task"
        )

        taskActor.tell(new GetStateMsg(), probe.getRef())
        state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.Stage.PREPARED,
                state.stage
        )


    }



}
