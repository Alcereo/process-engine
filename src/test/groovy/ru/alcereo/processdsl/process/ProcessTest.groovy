package ru.alcereo.processdsl.process

import akka.actor.*
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before
import ru.alcereo.processdsl.task.PersistFSMTask

import java.nio.file.Paths
/**
 * Created by alcereo on 05.01.18.
 */
class ProcessTest extends GroovyTestCase {

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

    static class PrintTaskActor extends PersistFSMTask{

        final String fieldToPrint

        PrintTaskActor(String persistentId, String fieldToPrint) {
            super(persistentId)
            this.fieldToPrint = fieldToPrint
        }

        static Props props(String persistentId, String fieldToPrint){
            Props.create(
                    PrintTaskActor.class,
                    { -> new PrintTaskActor(persistentId, fieldToPrint)}
            )
        }

        @Override
        void handleExecution(PersistFSMTask.TaskStateData taskStateData) {
            println " ======= EXECUTED: ${taskStateData.properties.get(fieldToPrint)} ======= "
        }

        @Override
        void handlePrepare(PersistFSMTask.TaskStateData taskStateData) {
            println " ======= PREPARED: ${taskStateData.properties.get(fieldToPrint)} ======= "
        }
    }


    void testProcessCreation(){
        def process = system.actorOf(Process.props("persistent-process-1"))
        def probe = new TestKit(system)


        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.NEW)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertEquals(
                0,
                stateData.taskContextSet.size()
        )

        Class<PrintTaskActor> classVar = PrintTaskActor.class

    }

    void testAddingContextToList(){
        def process = system.actorOf(Process.props("persistent-process-1"))
        def probe = new TestKit(system)

        def contextTask1 = new Process.TaskExecutionContext(
                UUID.randomUUID(),
                PrintTaskActor.props("test-actor-1", "text"),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)

        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.PREPARING)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertEquals(
                contextTask1,
                stateData.taskContextSet[0]
        )

    }

    void testAddingSecondContext(){
        def process = system.actorOf(Process.props("persistent-process-1"))
        def probe = new TestKit(system)

        def contextSet
        def contextTask1 = new Process.TaskExecutionContext(
                UUID.randomUUID(),
                PrintTaskActor.props("test-actor-1", "text"),
                [],
                [],
                []
        )

        def contextTask2 = new Process.TaskExecutionContext(
                UUID.randomUUID(),
                PrintTaskActor.props("test-actor-2", "text2"),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)

        process.tell(new Process.AddLastTaskCmd(contextTask2), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)


        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.PREPARING)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertTrue(stateData.taskContextSet.contains(contextTask1))
        assertTrue(stateData.taskContextSet.contains(contextTask2))

        assertEquals(
                2,
                stateData.taskContextSet.size()
        )
    }

    void testDupleSecondContext(){
        def process = system.actorOf(Process.props("persistent-process-1"))
        def probe = new TestKit(system)

        def identifier = UUID.randomUUID()

        def contextTask1 = new Process.TaskExecutionContext(
                identifier,
                PrintTaskActor.props("test-actor-1", "text"),
                [],
                [],
                []
        )

        def contextTask2 = new Process.TaskExecutionContext(
                identifier,
                PrintTaskActor.props("test-actor-2", "text2"),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)

        process.tell(new Process.AddLastTaskCmd(contextTask2), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)


        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.PREPARING)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertEquals(
                contextTask2.taskProp,
                stateData.taskContextSet[0].taskProp
        )

        assertNotSame(
                contextTask1.taskProp,
                stateData.taskContextSet[0].taskProp
        )

        assertEquals(
                1,
                stateData.taskContextSet.size()
        )
    }

    void testSetToReadyState(){
        def process = system.actorOf(Process.props("persistent-process-1"))
        def probe = new TestKit(system)

        def contextTask1 = new Process.TaskExecutionContext(
                UUID.randomUUID(),
                PrintTaskActor.props("test-actor-1", "text"),
                [],
                [],
                []
        )

        def contextTask2 = new Process.TaskExecutionContext(
                UUID.randomUUID(),
                PrintTaskActor.props("test-actor-2", "text2"),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)

        process.tell(new Process.AddLastTaskCmd(contextTask2), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)


        process.tell(new Process.SetReadyCmd(), probe.getRef())
        probe.expectMsgClass(Process.SuccessGoToReady.class)

        println " ---- CHECK STATE ----- "

        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.READY)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertTrue(stateData.taskContextSet.contains(contextTask1))
        assertTrue(stateData.taskContextSet.contains(contextTask2))

        assertEquals(
                2,
                stateData.taskContextSet.size()
        )

        process.tell(new Process.GetChildsCmd(), probe.getRef())
        def childList = probe.expectMsgClass(Process.ChildTaskList.class)

        for (child in childList.tasks){
            child.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
            probe.expectMsg(PersistFSMTask.TaskState.NEW)
        }
    }

    void testExceptionOnTaskAdding(){
        def process = system.actorOf(Process.props("persistent-process-1"), "process-1")
        def probe = new TestKit(system)

        def identifier = UUID.randomUUID()
        def contextTask1 = new Process.TaskExecutionContext(
                identifier,
                Props.empty(),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskAddingError.class)

    }

    void testRecover(){
        def process = system.actorOf(Process.props("persistent-process-1"), "process-1")
        def probe = new TestKit(system)

        def identifier = UUID.randomUUID()
        def contextTask1 = new Process.TaskExecutionContext(
                identifier,
                PrintTaskActor.props("test-actor-1", "text"),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)

        process.tell(new Process.SetReadyCmd(), probe.getRef())
        probe.expectMsgClass(Process.SuccessGoToReady.class)

        println "========  CHECK STATE =========="

        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.READY)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertEquals(
                contextTask1,
                stateData.taskContextSet[0]
        )

        process.tell(new Process.GetChildsCmd(), probe.getRef())
        def childList = probe.expectMsgClass(Process.ChildTaskList.class)

        for (child in childList.tasks){
            child.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
            probe.expectMsg(PersistFSMTask.TaskState.NEW)
        }

        println "========  KIKLL =========="

        probe.watch(process)
        process.tell(PoisonPill.instance, ActorRef.noSender())
        probe.expectMsgClass(Terminated.class)

        println "========  RECOVER =========="

        process = system.actorOf(Process.props("persistent-process-1"), "process-2")

        for (child in childList.tasks){
            child.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
            probe.expectNoMsg()
        }

        println "========  CHECK STATE =========="

        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.READY)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        stateData = probe.expectMsgClass(Process.StateData.class)

        assertEquals(
                1,
                stateData.taskContextSet.size()
        )

        process.tell(new Process.GetChildsCmd(), probe.getRef())
        childList = probe.expectMsgClass(Process.ChildTaskList.class)

        for (child in childList.tasks){
            child.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
            probe.expectMsg(PersistFSMTask.TaskState.NEW)
        }
    }

    void testRecoverException(){
        def process = system.actorOf(Process.props("persistent-process-1"), "process-1")
        def probe = new TestKit(system)

        def identifier = UUID.randomUUID()
        def contextTask1 = new Process.TaskExecutionContext(
                identifier,
                PrintTaskActor.props("test-actor-1", "text"),
                [],
                [],
                []
        )

        process.tell(new Process.AddLastTaskCmd(contextTask1), probe.getRef())
        probe.expectMsgClass(Process.TaskSuccessAdded.class)

        process.tell(new Process.SetReadyCmd(), probe.getRef())
        probe.expectMsgClass(Process.SuccessGoToReady.class)

        println "========  CHECK STATE =========="

        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.READY)

        process.tell(new Process.GetStateDataCmd(), probe.getRef())
        def stateData = probe.expectMsgClass(Process.StateData.class)

        assertEquals(
                contextTask1,
                stateData.taskContextSet[0]
        )

        process.tell(new Process.GetChildsCmd(), probe.getRef())
        def childList = probe.expectMsgClass(Process.ChildTaskList.class)

        for (child in childList.tasks){
            child.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
            probe.expectMsg(PersistFSMTask.TaskState.NEW)
        }

        println "========  KIKLL =========="

        probe.watch(process)
        process.tell(PoisonPill.instance, ActorRef.noSender())
        probe.expectMsgClass(Terminated.class)

        println "========  RECOVER =========="

        process = system.actorOf(
                Props.create(Process.class, { ->
                    new Process(
                            "persistent-process-1",
                            true,
                            identifier.toString()
                    )
                }), "process-2")

        for (child in childList.tasks){
            child.tell(new PersistFSMTask.GetStateCmd(), probe.getRef())
            probe.expectNoMsg()
        }

        println "========  CHECK STATE =========="

        process.tell(new Process.GetStateCmd(), probe.getRef())
        probe.expectMsg(Process.State.RECOVERING_ERROR)

    }

}
