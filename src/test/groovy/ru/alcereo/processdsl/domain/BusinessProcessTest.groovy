package ru.alcereo.processdsl.domain

import ru.alcereo.processdsl.domain.task.AbstractTask
import ru.alcereo.processdsl.domain.task.OneDirectionTask
import ru.alcereo.processdsl.domain.task.ProcessSuccessResultTask
import ru.alcereo.processdsl.domain.task.PropertiesExchangeData

import static org.mockito.Mockito.mock

class BusinessProcessTest extends GroovyTestCase {


    void testInitialisationProcess(){

        AbstractTask headerTask = mock(AbstractTask)

        def process = BusinessProcess.builder()
                .identifier(UUID.randomUUID())
                .headerTask(headerTask)
                .processContext([:])
                .build()

        println process

        assertEquals(
                headerTask,
                process.getCurrentTask()
        )

    }

    void testTaskResultStep(){

        def firstUid = UUID.randomUUID()
        def secondUid = UUID.randomUUID()
        def thirdUuid = UUID.randomUUID()

        AbstractTask lastMockedTask = mock(AbstractTask)


        def thirdTask = createOneWayTask(thirdUuid, lastMockedTask)
        def secondTask = createOneWayTask(secondUid, thirdTask)
        def firstTask = createOneWayTask(firstUid, secondTask)


        def process = BusinessProcess.builder()
                .identifier(UUID.randomUUID())
                .headerTask(firstTask)
                .processContext([:])
                .build()

        assertEquals(
                firstTask,
                process.getCurrentTask()
        )


        process.acceptCurrentTaskResult(new AbstractTask.SuccessTaskResult(firstUid, [:]))
        assertEquals(
                secondTask,
                process.getCurrentTask()
        )


        process.acceptCurrentTaskResult(new AbstractTask.SuccessTaskResult(secondUid, [:]))
        assertEquals(
                thirdTask,
                process.getCurrentTask()
        )


        process.acceptCurrentTaskResult(new AbstractTask.SuccessTaskResult(thirdUuid, [:]))
        assertEquals(
                lastMockedTask,
                process.getCurrentTask()
        )

    }

    void testFinishProcess(){

        def firstUid = UUID.randomUUID()

        def resultUid = UUID.randomUUID()
        AbstractTask resultTask = ProcessSuccessResultTask
                .builder()
                .identifier(resultUid)
                .build()

        def firstTask = createOneWayTask(firstUid, resultTask)


        def process = BusinessProcess.builder()
                .identifier(UUID.randomUUID())
                .headerTask(firstTask)
                .processContext([:])
                .build()

        assertEquals(
                firstTask,
                process.getCurrentTask()
        )

        assertEquals(
                false,
                process.isFinished()
        )


        process.acceptCurrentTaskResult(new AbstractTask.SuccessTaskResult(firstUid, [:]))

        assertEquals(
                true,
                process.isFinished()
        )

        assertEquals(
                resultTask,
                process.getCurrentTask()
        )

        shouldFail(AcceptResultOnFinishException) {
            process.acceptCurrentTaskResult(new AbstractTask.SuccessTaskResult(resultUid, [:]))
        }

        assertEquals(
                true,
                process.isFinished()
        )

        assertEquals(
                resultTask,
                process.getCurrentTask()
        )

    }

    void testContainsTaskIdentifier(){

        def firstUid = UUID.randomUUID()
        def secondUid = UUID.randomUUID()
        def thirdUuid = UUID.randomUUID()

        AbstractTask lastMockedTask = mock(AbstractTask)


        def thirdTask = createOneWayTask(thirdUuid, lastMockedTask)
        def secondTask = createOneWayTask(secondUid, thirdTask)
        def firstTask = createOneWayTask(firstUid, secondTask)


        def process = BusinessProcess.builder()
                .identifier(UUID.randomUUID())
                .headerTask(firstTask)
                .processContext([:])
                .build()

        assertEquals(
                firstTask,
                process.getCurrentTask()
        )

        assertEquals(
                true,
                process.containsTaskIdentifier(firstUid)
        )

        assertEquals(
                true,
                process.containsTaskIdentifier(secondUid)
        )

        assertEquals(
                true,
                process.containsTaskIdentifier(thirdUuid)
        )

        assertEquals(
                false,
                process.containsTaskIdentifier(UUID.randomUUID())
        )
    }

    void testGetAllTasks(){

        def resultUid = UUID.randomUUID()
        def firstUid = UUID.randomUUID()
        def secondUid = UUID.randomUUID()
        def thirdUuid = UUID.randomUUID()

        AbstractTask resultTask = ProcessSuccessResultTask
                .builder()
                .identifier(resultUid)
                .build()
        def thirdTask = createOneWayTask(thirdUuid, resultTask)
        def secondTask = createOneWayTask(secondUid, thirdTask)
        def firstTask = createOneWayTask(firstUid, secondTask)


        def process = BusinessProcess.builder()
                .identifier(UUID.randomUUID())
                .headerTask(firstTask)
                .processContext([:])
                .build()


        assertEquals(
                [firstTask, secondTask, thirdTask, resultTask],
                process.getAllTasks()
        )

    }


    /**========================================*
     *                Utils                   *
     *=========================================*/


    static AbstractTask createOneWayTask(UUID taskUid, AbstractTask nextTask){

        PropertiesExchangeData propertiesExchangeData = PropertiesExchangeData.builder()
                .innerPropsFromContext([])
                .innerPropsFromLastOutput([])
                .outerPropsToContext([])
                .build()

        return OneDirectionTask.builder()
                .identifier(taskUid)
                .properties([:])
                .propertiesExchangeData(propertiesExchangeData)
                .nextTask(nextTask)
                .type({ -> null })
                .build()
    }

}
