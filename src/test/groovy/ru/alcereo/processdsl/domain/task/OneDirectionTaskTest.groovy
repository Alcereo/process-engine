package ru.alcereo.processdsl.domain.task

import static org.mockito.Mockito.mock

class OneDirectionTaskTest extends GroovyTestCase {


    void testOneWayTaskExchangeContext(){

        def value1 = UUID.randomUUID().toString()
        def value2 = UUID.randomUUID().toString()


        AbstractTask.SuccessTaskResult previousResult = new AbstractTask.SuccessTaskResult(
                UUID.randomUUID(),
                [prevProp1:value1,
                 prevProp2:value2]
        )

        UUID taskUid = UUID.randomUUID()


        AbstractTask nextTaskMock = mock(AbstractTask)

        PropertiesExchangeData propertiesExchangeData = PropertiesExchangeData.builder()
                .innerPropsFromContext([
                PropertiesExchangeData.PropMappingData.of("innerParentProp1", "parentProp1")
        ])
                .innerPropsFromLastOutput([
                PropertiesExchangeData.PropMappingData.of("innerPrevProp2", "prevProp2")
        ])
                .outerPropsToContext([])
                .build()

        AbstractTask task = OneDirectionTask.builder()
                .identifier(taskUid)
                .properties([prop3:value1])
                .propertiesExchangeData(propertiesExchangeData)
                .nextTask(nextTaskMock)
                .type({ -> null })
                .build()

        Map<String, Object> parentContext =
                [parentProp1:value1,
                 parentProp2:value2]

        task.acceptDataToStart(previousResult, parentContext)

        def resultProps = task.getProperties()

        assertEquals(
                value1,
                resultProps["innerParentProp1"]
        )

        assertEquals(
                value2,
                resultProps["innerPrevProp2"]
        )

        assertEquals(
                value1,
                resultProps["prop3"]
        )

        assertEquals(
                3,
                resultProps.size()
        )



        def nextTaskAfterResult = task.getNextTaskByResult(new AbstractTask.SuccessTaskResult(taskUid, [:]))

        assertEquals(
                nextTaskMock,
                nextTaskAfterResult
        )
    }


}
