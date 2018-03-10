package ru.alcereo.processdsl.task

import akka.actor.ActorRef
import akka.actor.Props
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest

class GroovyCalculationTaskActorTest extends ActorSystemInitializerTest {


    void testSimplePrint(){

        TestKit probe = new TestKit(system)
        UUID taskUuid = UUID.randomUUID()

        ActorRef taskActor = probe.childActorOf(
                Props.create(GroovyCalculationTaskActor.class, { -> new GroovyCalculationTaskActor(taskUuid)})
                , "task")

        System.out.println("----    Start append context   -----")

        String script = """
            println context.get("text-to-print")
        """

        taskActor.tell(
                new PersistFSMTask.AppendToContextCmd(
                        [
                                "script-property":script,
                                "text-to-print": "---------- TEST TEXT TO PRINT ----------"
                        ]
                ),
                probe.getRef())
        probe.expectMsgClass(PersistFSMTask.CmdSuccess.class)

        System.out.println("----   Append context finish   -----")
        System.out.println("----       Start execution     -----")

        taskActor.tell(
                new PersistFSMTask.StartExecutingCmd(),
                probe.getRef()
        )
        probe.expectMsgClass(PersistFSMTask.CmdSuccess.class)

        System.out.println("---- Finish command execution -----")

        probe.expectMsgClass(PersistFSMTask.SuccessExecutedEvt.class)

        System.out.println("----  Get succesfull message  -----")

        taskActor.tell(
                new PersistFSMTask.GetStateDataQuery(),
                probe.getRef()
        )
        probe.expectMsgClass(PersistFSMTask.TaskDataState.class)

    }

}
