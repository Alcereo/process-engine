package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.List;
import java.util.Map;
import java.util.UUID;


public class OneDirectionTask extends DecisionTask {

    @Builder
    public OneDirectionTask(UUID identifier,
                            Map<String, Object> properties,
                            PropertiesExchangeData propertiesExchangeData,
                            TaskActorType type,
                             List<AbstractTask> taskList) {
        super(identifier, properties, propertiesExchangeData, type, taskList);

        if (taskList.size()!=1)
            throw new IllegalArgumentException("Task list must have 1 elements in OneDirectionTask");
    }

    @Override
    protected AbstractTask getNexTaskByResultAndContext(TaskResult result, List<AbstractTask> taskList) {
        return taskList.get(0);
    }
}
