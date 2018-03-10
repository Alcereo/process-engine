package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import lombok.NonNull;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SimpleResultDecisionTask extends DecisionTask{

    @Builder(builderClassName = "TaskBuilder")
    private SimpleResultDecisionTask(@NonNull UUID identifier,
                                     Map<String, Object> properties,
                                     PropertiesExchangeData propertiesExchangeData,
                                     @NonNull AbstractTask successResultTask,
                                     @NonNull AbstractTask failureResultTask) {
        super(identifier, properties, propertiesExchangeData, Arrays.asList(successResultTask, failureResultTask));
    }

    @Override
    protected AbstractTask getNexTaskByResultAndContext(TaskResult result, List<AbstractTask> taskList) {
        if (result.isSuccess())
            return taskList.get(0);
        else
            return taskList.get(1);
    }
}
