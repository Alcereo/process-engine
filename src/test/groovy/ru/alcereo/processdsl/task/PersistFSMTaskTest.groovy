package ru.alcereo.processdsl.task

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import groovy.transform.NotYetImplemented
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

//    static class PrintTaskActor extends PersistFSMTask{
//
//        PrintTaskActor(String persistentId) {
//            super(persistentId)
//        }
//
//        static Props props(String persistentId){
//            Props.create(
//                    PrintTaskActor.class,
//                    { -> new PrintTaskActor(persistentId)}
//            )
//        }
//
//        @Override
//        void handleExecution(Task taskStateData) {
//            println " -- EXECUTED: ${taskStateData.properties.get('text')} -- "
//        }
//
//        @Override
//        void handlePrepare(Task taskStateData) {
//            println " -- PREPARED: ${taskStateData.properties.get('text')} -- "
//        }
//    }

    @NotYetImplemented
    void testPersisActor(){}

//    void testPersisActor(){
//        def taskActor = system.actorOf(
//                PrintTaskActor.props("actor-persist-id"),
//                "task"
//        )
//
//        def probe = new TestKit(system)
//        def state
//
//        taskActor.tell(new GetStateDataQuery(), probe.getRef())
//        state = probe.expectMsgClass(Task.class)
//
//        assertEquals(
//                [:],
//                state.properties
//        )
//
//        taskActor.tell(new GetTaskStateQuery(), probe.getRef())
//        state = probe.expectMsgClass(TaskState.class)
//
//        assertEquals(
//                TaskState.NEW,
//                state
//        )
//
//    }
//
//
//    void testStateReceiveOnExit() {
//        def taskActor
//        def probe = new TestKit(system)
//        def state
//
//        taskActor = system.actorOf(
//                PrintTaskActor.props("actor-persist-id"),
//                "task"
//        )
//
//        def checkState = { Map msg, TaskState stateEnum ->
//            taskActor.tell(new GetStateDataQuery(), probe.getRef())
//            state = probe.expectMsgClass(Task.class)
//
//            assertEquals(
//                    msg,
//                    state.properties
//            )
//
//            taskActor.tell(new GetTaskStateQuery(), probe.getRef())
//            state = probe.expectMsgClass(TaskState.class)
//
//            assertEquals(
//                    stateEnum,
//                    state
//            )
//        }
//
//
//        println " ------ Check state - NEW --------- "
//        checkState([:], TaskState.NEW)
//
//
//        println " ------ Send prepared command --------- "
//        def textToPrint = "This text will printed"
//        def preparedProps = [
//                "text": textToPrint
//        ]
//        taskActor.tell(new SetContextCmd(preparedProps), probe.getRef())
//        def preparedEvt = probe.expectMsgClass(ContextSetEvt.class)
//        assertEquals(
//                preparedProps,
//                preparedEvt.properties
//        )
//
//
//        println " ------ Check state - PREPARED --------- "
//        checkState(preparedProps, TaskState.PREPARED)
//
//
//        println " ------ KILL --------- "
//        taskActor.tell(PoisonPill.instance, ActorRef.noSender())
//        Thread.sleep(100)  // Нужно нормальное подтверждение
//        taskActor = system.actorOf(
//                PrintTaskActor.props("actor-persist-id"),
//                "task"
//        )
//        Thread.sleep(100)  // Нужно нормальное подтверждение
//
//
//        println " ------ Check state - PREPARED --------- "
//        checkState(preparedProps, TaskState.PREPARED)
//
//    }
//
//
//    void testExecuteTask(){
//
//        def taskActor
//        def probe = new TestKit(system)
//        def state
//
//        taskActor = system.actorOf(
//                PrintTaskActor.props("actor-persist-id"),
//                "task"
//        )
//
//        def checkState = { Map msg, TaskState stateEnum ->
//            taskActor.tell(new GetStateDataQuery(), probe.getRef())
//            state = probe.expectMsgClass(Task.class)
//
//            assertEquals(
//                    msg,
//                    state.properties
//            )
//
//            taskActor.tell(new GetTaskStateQuery(), probe.getRef())
//            state = probe.expectMsgClass(TaskState.class)
//
//            assertEquals(
//                    stateEnum,
//                    state
//            )
//        }
//
//
//        println " ------ Send prepared command --------- "
//        def textToPrint = "This text will printed"
//        def preparedProps = [
//                "text": textToPrint
//        ]
//        taskActor.tell(new SetContextCmd(preparedProps), probe.getRef())
//        probe.expectMsgClass(ContextSetEvt)
//
//        taskActor.tell(new StartExecutingCmd(), probe.getRef())
//        probe.expectMsgClass(ExecutionStarted)
//
//        checkState(preparedProps, TaskState.EXECUTED)
//
//        taskActor.tell(new SuccessFinishExecutingCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessFinishExecutingCmd)
//
//        checkState(preparedProps, TaskState.SUCCESS_FINISHED)
//
//    }

}
