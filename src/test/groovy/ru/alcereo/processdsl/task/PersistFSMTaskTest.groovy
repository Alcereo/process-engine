package ru.alcereo.processdsl.task

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before

import java.nio.file.Paths

/**
 * Created by alcereo on 03.01.18.
 */
class PersistFSMTaskTest extends GroovyTestCase {

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
                PersistFSMTask.props("actor-persist-id"),
                "task"
        )

        def probe = new TestKit(system)
        def state

        taskActor.tell(new PersistFSMTask.GetStateDataCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskStateData.class)

        assertEquals(
                "",
                state.textToPrint
        )

        taskActor.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskState.class)

        assertEquals(
                PersistFSMTask.TaskState.NEW,
                state
        )

    }


    void testStateReceiveOnExit() {
        def taskActor
        def probe = new TestKit(system)
        def state

        taskActor = system.actorOf(
                PersistFSMTask.props("actor-persist-id"),
                "task"
        )

//        Check state - NEW

        println " ------ Check state - NEW --------- "

        taskActor.tell(new PersistFSMTask.GetStateDataCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskStateData.class)

        assertEquals(
                "",
                state.textToPrint
        )

        taskActor.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskState.class)

        assertEquals(
                PersistFSMTask.TaskState.NEW,
                state
        )

//        Send prepare

        println " ------ Send prepared command --------- "

        def preparedProps = [
                "text": "This text will printed"
        ]

        taskActor.tell(new PersistFSMTask.PrepareCmd(preparedProps), probe.getRef())
        def preparedEvt = probe.expectMsgClass(PersistFSMTask.PreparedEvt.class)

        assertEquals(
                "This text will printed",
                preparedEvt.textToPrint
        )

//        Check state - PREPARED

        println " ------ Check state - PREPARED --------- "

        taskActor.tell(new PersistFSMTask.GetStateDataCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskStateData.class)

        assertEquals(
                "This text will printed",
                state.textToPrint
        )

        taskActor.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskState.class)

        assertEquals(
                PersistFSMTask.TaskState.PREPARED,
                state
        )

//        KILL

        println " ------ KILL --------- "

        taskActor.tell(PoisonPill.instance, ActorRef.noSender())

        taskActor = system.actorOf(
                PersistFSMTask.props("actor-persist-id"),
                "task"
        )

        Thread.sleep(100)

//        Check same state

        println " ------ Check state - PREPARED --------- "

        taskActor.tell(new PersistFSMTask.GetStateDataCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskStateData.class)

        assertEquals(
                "This text will printed",
                state.textToPrint
        )

        taskActor.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
        state = probe.expectMsgClass(PersistFSMTask.TaskState.class)

        assertEquals(
                PersistFSMTask.TaskState.PREPARED,
                state
        )

    }
}
