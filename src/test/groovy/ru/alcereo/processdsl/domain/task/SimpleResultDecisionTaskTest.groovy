package ru.alcereo.processdsl.domain.task

import static org.mockito.Mockito.mock

class SimpleResultDecisionTaskTest extends GroovyTestCase {


    void testDecisionTask(){

        def successTaskMock = mock(AbstractTask)
        def failureTaskMock = mock(AbstractTask)


        PropertiesExchangeData propertiesExchangeData = PropertiesExchangeData.builder()
                .innerPropsFromContext([])
                .innerPropsFromLastOutput([])
                .outerPropsToContext([])
                .build()


        AbstractTask task = SimpleResultDecisionTask.builder()
                .identifier(UUID.randomUUID())
                .properties([:])
                .propertiesExchangeData(propertiesExchangeData)
                .taskList([successTaskMock, failureTaskMock])
                .type({ -> null })
                .build()


        def successTaskResult = task.getNextTaskByResult(new AbstractTask.SuccessTaskResult(UUID.randomUUID(), [:]))

        assertEquals(
                successTaskMock,
                successTaskResult
        )

        def failureTaskResult = task.getNextTaskByResult(new AbstractTask.FailureTaskResult(UUID.randomUUID(), new Throwable() ,[:]))

        assertEquals(
                failureTaskMock,
                failureTaskResult
        )
    }
}
