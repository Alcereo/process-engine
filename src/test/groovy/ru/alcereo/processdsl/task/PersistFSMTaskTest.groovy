package ru.alcereo.processdsl.task

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.PoisonPill
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before

import java.nio.file.Paths

import static ru.alcereo.processdsl.task.PersistFSMTask.*

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
                props("actor-persist-id"),
                "task"
        )

        def probe = new TestKit(system)
        def state

        taskActor.tell(new GetStateDataCmd(), probe.getRef())
        state = probe.expectMsgClass(TaskStateData.class)

        assertEquals(
                "",
                state.textToPrint
        )

        taskActor.tell(new GetStateCmd(), probe.getRef())
        state = probe.expectMsgClass(TaskState.class)

        assertEquals(
                TaskState.NEW,
                state
        )

    }


    void testStateReceiveOnExit() {
        def taskActor
        def probe = new TestKit(system)
        def state

        taskActor = system.actorOf(
                props("actor-persist-id"),
                "task"
        )

        def checkState = { String msg, TaskState stateEnum ->
            taskActor.tell(new GetStateDataCmd(), probe.getRef())
            state = probe.expectMsgClass(TaskStateData.class)

            assertEquals(
                    msg,
                    state.textToPrint
            )

            taskActor.tell(new GetStateCmd(), probe.getRef())
            state = probe.expectMsgClass(TaskState.class)

            assertEquals(
                    stateEnum,
                    state
            )
        }


        println " ------ Check state - NEW --------- "
        checkState("", TaskState.NEW)


        println " ------ Send prepared command --------- "
        def textToPrint = "This text will printed"
        def preparedProps = [
                "text": textToPrint
        ]
        taskActor.tell(new PrepareCmd(preparedProps), probe.getRef())
        def preparedEvt = probe.expectMsgClass(PreparedEvt.class)
        assertEquals(
                textToPrint,
                preparedEvt.textToPrint
        )


        println " ------ Check state - PREPARED --------- "
        checkState(textToPrint, TaskState.PREPARED)


        println " ------ KILL --------- "
        taskActor.tell(PoisonPill.instance, ActorRef.noSender())
        Thread.sleep(100)  // Нужно нормальное подтверждение
        taskActor = system.actorOf(
                props("actor-persist-id"),
                "task"
        )
        Thread.sleep(100)  // Нужно нормальное подтверждение


        println " ------ Check state - PREPARED --------- "
        checkState(textToPrint, TaskState.PREPARED)

    }
}
