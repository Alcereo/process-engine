package ru.alcereo.processdsl.process

import akka.actor.ActorSystem
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import org.junit.After
import org.junit.Before
import ru.alcereo.processdsl.domain.BusinessProcess
import ru.alcereo.processdsl.domain.task.*

class DecisionTaskExecutingTest extends GroovyTestCase {

    ActorSystem system

    @Before
    void setUp() throws Exception {
        def config = ConfigFactory.load("test-config")
        system = ActorSystem.create("test-config", config)
    }

    @After
    void tearDown() throws Exception {
        system.terminate()
    }


    @SuppressWarnings("unchecked")
    void testSuccessDecisionExecuting() throws InterruptedException {
        decisionTestCase(true)
    }

    @SuppressWarnings("unchecked")
    void testFailureDecisionExecuting() throws InterruptedException {
        decisionTestCase(false)
    }

    void decisionTestCase(boolean successCase){

        def repository = Props.create(ProcessInMemoryRepository.class)
        def processApiActor = system.actorOf(ProcessActor.props(repository), "process-api")
        def probe = new TestKit(system)
        def observerProbe = new TestKit(system)

//        Create tasks

        AbstractTask successFinishTask = ProcessSuccessResultTask.builder()
                .identifier(UUID.randomUUID())
                .build()

        AbstractTask failureFinishTask = ProcessFailureResultTask.builder()
                .identifier(UUID.randomUUID())
                .build()

        UUID decisionTaskUuid = UUID.randomUUID()

        def builder = SimpleResultDecisionTask.builder()
                .identifier(decisionTaskUuid)
                .properties([:])
                .propertiesExchangeData(PropertiesExchangeData.empty())

        if (successCase)
            builder.successResultTask(successFinishTask)
                    .failureResultTask(failureFinishTask)
        else
            builder.successResultTask(failureFinishTask)
                    .failureResultTask(successFinishTask)


        def decisionTask = builder.build()


        System.out.println("------------------ Add observer")
        processApiActor.tell(new ProcessActor.AddObserverMsg(observerProbe.getRef()), probe.getRef())
        probe.expectMsgClass(ProcessActor.SuccessAdded.class)


        System.out.println("------------------ Create process")
        UUID processUuid = UUID.randomUUID()

        processApiActor.tell(
                new ProcessActor.CreateNewProcessCmd(processUuid, [:]),
                probe.getRef()
        )
        probe.expectMsgClass(ProcessActor.SuccessCommand.class)
        observerProbe.expectMsgClass(BusinessProcess.ProcessCreatedEvt.class)


        System.out.println("------------------ Add tasks to process")
        processApiActor.tell(new ProcessActor.SetTasksToProcessCmd(processUuid, decisionTask), probe.getRef())
        probe.expectMsgClass(ProcessActor.SuccessCommand.class)
        observerProbe.expectMsgClass(BusinessProcess.LastTaskAddedEvt.class)


        System.out.println("------------------ Start execution process")
        processApiActor.tell(new ProcessActor.StartProcessCmd(processUuid), probe.getRef())
        probe.expectMsgClass(ProcessActor.SuccessCommand.class)
        observerProbe.expectMsgClass(BusinessProcess.ProcessStartedEvt.class)


        System.out.println("------------------ Wait for finish")
        def resultEvt = observerProbe.expectMsgClass(BusinessProcess.ProcessFinishedEvt.class)

        assertEquals(
                successCase,
                resultEvt.isSuccess()
        )
    }

}