package ru.alcereo.processdsl.process

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import groovy.transform.NotYetImplemented
import org.junit.After
import org.junit.Before

import java.nio.file.Paths
/**
 * Created by alcereo on 05.01.18.
 */
class BusinessProcessActorTest extends GroovyTestCase {

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

    //TODO тест на таски и десижн таски


//    static class PrintTaskActor extends PersistFSMTask{
//
//        final String fieldToPrint
//
//        PrintTaskActor(String persistentId, String fieldToPrint) {
//            super(persistentId)
//            this.fieldToPrint = fieldToPrint
//        }
//
//        static Props props(String persistentId, String fieldToPrint){
//            Props.create(
//                    PrintTaskActor.class,
//                    { -> new PrintTaskActor(persistentId, fieldToPrint)}
//            )
//        }
//
//        @Override
//        void handleExecution(Task taskStateData) {
//            println "-- EXECUTED: ${taskStateData.properties.get(fieldToPrint)} --"
//        }
//
//        @Override
//        void handlePrepare(Task taskStateData) {
//            println "-- PREPARED: ${taskStateData.properties.get(fieldToPrint)} --"
//            println "Context: ${taskStateData.properties}"
//        }
//    }
//
//
//    void testProcessCreation(){
//        def process = system.actorOf(props("persistent-process-1"))
//        def probe = new TestKit(system)
//
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.NEW)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                0,
//                stateData.tasks.size()
//        )
//
//        Class<PrintTaskActor> classVar = PrintTaskActor.class
//
//    }
//
//    void testAddingContextToList(){
//        def process = system.actorOf(props("persistent-process-1"))
//        def probe = new TestKit(system)
//
//        def contextTask1 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.PREPARING)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                contextTask1,
//                stateData.tasks[0]
//        )
//
//    }
//
//    void testAddingSecondContext(){
//        def process = system.actorOf(props("persistent-process-1"))
//        def probe = new TestKit(system)
//
//        def contextSet
//        def contextTask1 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        def contextTask2 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-2", "text2"),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new AddLastTaskCmd(contextTask2), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.PREPARING)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                contextTask1,
//                stateData.getTasks()[0]
//        )
//
//        assertEquals(
//                contextTask2,
//                stateData.getTasks()[1]
//        )
//
//        assertEquals(
//                2,
//                stateData.getTasks().size()
//        )
//    }
//
//    void testDupleSecondContext(){
//        def process = system.actorOf(props("persistent-process-1"))
//        def probe = new TestKit(system)
//
//        def identifier = UUID.randomUUID()
//
//        def contextTask1 = new Task(
//                identifier,
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        def contextTask2 = new Task(
//                identifier,
//                PrintTaskActor.props("test-actor-2", "text2"),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new AddLastTaskCmd(contextTask2), probe.getRef())
//        probe.expectMsgClass(TaskAddingError.class)
//
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.PREPARING)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                contextTask1.taskProp,
//                stateData.tasks[0].taskProp
//        )
//
//        assertEquals(
//                1,
//                stateData.tasks.size()
//        )
//    }
//
//    void testSetToReadyState(){
//        def process = system.actorOf(props("persistent-process-1"))
//        def probe = new TestKit(system)
//
//        def contextTask1 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        def contextTask2 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-2", "text2"),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new AddLastTaskCmd(contextTask2), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//
//        process.tell(new SetReadyCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessGoToReady.class)
//
//        println " ---- CHECK STATE ----- "
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.READY)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                contextTask1,
//                stateData.getTasks()[0]
//        )
//
//        assertEquals(
//                contextTask2,
//                stateData.getTasks()[1]
//        )
//
//        assertEquals(
//                2,
//                stateData.getTasks().size()
//        )
//
//        process.tell(new GetChildsCmd(), probe.getRef())
//        def childList = probe.expectMsgClass(ChildTaskList.class)
//
//        for (child in childList.tasks){
//            child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//            probe.expectMsg(PersistFSMTask.TaskState.NEW)
//        }
//    }
//
//    @NotYetImplemented
//    void testExceptionOnGoToReadyState(){}
//
//    void testExceptionOnTaskAdding(){
//        def process = system.actorOf(props("persistent-process-1"), "process-1")
//        def probe = new TestKit(system)
//
//        def identifier = UUID.randomUUID()
//        def contextTask1 = new Task(
//                identifier,
//                Props.empty(),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskAddingError.class)
//
//    }
//
//    void testRecover(){
//        def process = system.actorOf(props("persistent-process-1"), "process-1")
//        def probe = new TestKit(system)
//
//        def identifier = UUID.randomUUID()
//        def contextTask1 = new Task(
//                identifier,
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new SetReadyCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessGoToReady.class)
//
//        println "========  CHECK STATE =========="
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.READY)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                contextTask1,
//                stateData.tasks[0]
//        )
//
//        process.tell(new GetChildsCmd(), probe.getRef())
//        def childList = probe.expectMsgClass(ChildTaskList.class)
//
//        for (child in childList.tasks){
//            child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//            probe.expectMsg(PersistFSMTask.TaskState.NEW)
//        }
//
//        println "========  KIKLL =========="
//
//        probe.watch(process)
//        process.tell(PoisonPill.instance, ActorRef.noSender())
//        probe.expectMsgClass(Terminated.class)
//
//        println "========  RECOVER =========="
//
//        process = system.actorOf(props("persistent-process-1"), "process-2")
//
//        for (child in childList.tasks){
//            child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//            probe.expectNoMsg()
//        }
//
//        println "========  CHECK STATE =========="
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.READY)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                1,
//                stateData.tasks.size()
//        )
//
//        process.tell(new GetChildsCmd(), probe.getRef())
//        childList = probe.expectMsgClass(ChildTaskList.class)
//
//        for (child in childList.tasks){
//            child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//            probe.expectMsg(PersistFSMTask.TaskState.NEW)
//        }
//    }
//
//    void testRecoverException(){
//        def process = system.actorOf(props("persistent-process-1"), "process-1")
//        def probe = new TestKit(system)
//
//        def identifier = UUID.randomUUID()
//        def contextTask1 = new Task(
//                identifier,
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new SetReadyCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessGoToReady.class)
//
//        println "========  CHECK STATE =========="
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.READY)
//
//        process.tell(new GetStateDataQuery(), probe.getRef())
//        def stateData = probe.expectMsgClass(BusinessProcess.class)
//
//        assertEquals(
//                contextTask1,
//                stateData.tasks[0]
//        )
//
//        process.tell(new GetChildsCmd(), probe.getRef())
//        def childList = probe.expectMsgClass(ChildTaskList.class)
//
//        for (child in childList.tasks){
//            child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//            probe.expectMsg(PersistFSMTask.TaskState.NEW)
//        }
//
//        println "========  KIKLL =========="
//
//        probe.watch(process)
//        process.tell(PoisonPill.instance, ActorRef.noSender())
//        probe.expectMsgClass(Terminated.class)
//
//        println "========  RECOVER =========="
//
//        process = system.actorOf(
//                Props.create(ProcessActor.class, { ->
//                    new ProcessActor(
//                            "persistent-process-1",
//                            true,
//                            identifier.toString()
//                    )
//                }), "process-2")
//
//        for (child in childList.tasks){
//            child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//            probe.expectNoMsg()
//        }
//
//        println "========  CHECK STATE =========="
//
//        process.tell(new GetStateCmd(), probe.getRef())
//        probe.expectMsg(State.RECOVERING_ERROR)
//
//    }

    @NotYetImplemented
    void testChildKillStrategy(){}

//    void testSettingProcessContext(){
//
//        def process = system.actorOf(props("persistent-process-1"), "process-1")
//        def probe = new TestKit(system)
//
//        process.tell(new GetContextCmd(), probe.getRef())
//        def context = probe.expectMsgClass(ProcessContextMessage)
//
//        assertEquals(
//                0,
//                context.processContext.size()
//        )
//
//        process.tell(new SetContextCmd(["test":"some"]), probe.getRef())
//        probe.expectMsgClass(SuccessSetContext)
//
//        process.tell(new GetContextCmd(), probe.getRef())
//
//        assertEquals(
//                ["test":"some"],
//                probe.expectMsgClass(ProcessContextMessage).processContext
//        )
//
//        process.tell(new AppendToContextCmd(["test":"some1", "test2":"some2"]), probe.getRef())
//        probe.expectMsgClass(SuccessSetContext)
//
//        process.tell(new GetContextCmd(), probe.getRef())
//
//        assertEquals(
//                ["test":"some1", "test2":"some2"],
//                probe.expectMsgClass(ProcessContextMessage).processContext
//        )
//
//        process.tell(new AppendToContextCmd(["test3":"some3"]), probe.getRef())
//        probe.expectMsgClass(SuccessSetContext)
//
//        process.tell(new GetContextCmd(), probe.getRef())
//
//        assertEquals(
//                ["test":"some1", "test2":"some2", "test3":"some3"],
//                probe.expectMsgClass(ProcessContextMessage).processContext
//        )
//
//    }
//
//    void testTasksStateOnReady(){
//
//        def process = system.actorOf(props("persistent-process-1"), "process-1")
//        def probe = new TestKit(system)
//
//        def identifier1 = UUID.randomUUID()
//        def contextTask1 = new Task(
//                identifier1,
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//        def identifier2 = UUID.randomUUID()
//        def contextTask2 = new Task(
//                identifier2,
//                PrintTaskActor.props("test-actor-1", "text"),
//                [],
//                [],
//                []
//        )
//
//
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new AddLastTaskCmd(contextTask2), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//
//        process.tell(new SetReadyCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessGoToReady.class)
//
//        process.tell(new GetTasksStatesCmd(), probe.getRef())
//        def statesMessage = probe.expectMsgClass(TasksStatesMessage.class)
//
//
//        def keys = statesMessage.tasksStatuses.keySet()
//        assertTrue(keys.contains(identifier1))
//        assertTrue(keys.contains(identifier2))
//
//
//        def statesSet = new HashSet<PersistFSMTask.TaskState>(statesMessage.tasksStatuses.values())
//        assertTrue(statesSet.contains(PersistFSMTask.TaskState.NEW))
//        assertTrue(statesSet.size()==1)
//
//    }
//
//    void testProcessStart(){
//
//        def process = system.actorOf(props("persistent-process-1"), "process-1")
//        def probe = new TestKit(system)
//
//        def fieldToPrint1 = "text"
//        def contextProperty1 = "some-text"
//        def someText1 = "Some text to print 1"
//
//        def fieldToPrint2 = "text2"
//        def contextProperty2 = "some-text2"
//        def someText2 = "Some text to print 2"
//
//        def contextTask1 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-1", fieldToPrint1),
//                [Tuple2.apply(contextProperty1, fieldToPrint1)],
//                [],
//                []
//        )
//
//        def contextTask2 = new Task(
//                UUID.randomUUID(),
//                PrintTaskActor.props("test-actor-1", fieldToPrint2),
//                [Tuple2.apply(contextProperty2, fieldToPrint2)],
//                [],
//                []
//        )
//
//        def contextMap = new HashMap<String, Object>()
//        contextMap.put(contextProperty1, someText1)
//        contextMap.put(contextProperty2, someText2)
//
//        println "=======SETTING=CONTEXT========"
//        process.tell(new SetContextCmd(contextMap), probe.getRef())
//        probe.expectMsgClass(SuccessSetContext)
//
//        println "=========ADDING=TASK==========="
//        process.tell(new AddLastTaskCmd(contextTask1), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        process.tell(new AddLastTaskCmd(contextTask2), probe.getRef())
//        probe.expectMsgClass(TaskSuccessAdded.class)
//
//        println "==========GO=TO=READY=========="
//        process.tell(new SetReadyCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessGoToReady.class)
//
//        Thread.sleep(100)
//
//        println "==========GO=TO=START=========="
//        process.tell(new StartProcessCmd(), probe.getRef())
//        probe.expectMsgClass(SuccessStartProcess.class)
//
//        println "=======CHECK=TASK=STATE========"
//
//        process.tell(new GetChildsCmd(), probe.getRef())
//        def childList = probe.expectMsgClass(ChildTaskList.class)
//
//        def child = childList.tasks[0]
//        child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//        probe.expectMsg(PersistFSMTask.TaskState.EXECUTED)
//
//        child = childList.tasks[1]
//        child.tell(new PersistFSMTask.GetTaskStateQuery(), probe.getRef())
//        probe.expectMsg(PersistFSMTask.TaskState.NEW)
//
//
//    }

}
