package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class SimpleResultDecisionTask extends DecisionTask{

    @Builder(builderClassName = "TaskBuilder")
    private SimpleResultDecisionTask(UUID identifier,
                                     Map<String, Object> properties,
                                     PropertiesExchangeData propertiesExchangeData,
                                     TaskActorType type,
                                     List<AbstractTask> taskList) {
        super(identifier, properties, propertiesExchangeData, type, taskList);

        if (taskList.size()!=2)
            throw new IllegalArgumentException("Task list must have 2 elements in SimpleResultDecisionTask");
    }

    @Override
    protected AbstractTask getNexTaskByResultAndContext(TaskResult result, List<AbstractTask> taskList) {
        if (result.isSuccess())
            return taskList.get(0);
        else
            return taskList.get(1);
    }
}
