package ru.alcereo.processdsl.domain.task;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NonNull;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class DecisionTask extends AbstractTask {

    @NonNull
    @Getter(AccessLevel.PROTECTED)
    private final List<AbstractTask> taskList;

    protected DecisionTask(UUID identifier,
                           Map<String, Object> properties,
                           PropertiesExchangeData propertiesExchangeData,
                           @NonNull List<AbstractTask> taskList) {

        super(identifier, properties, propertiesExchangeData, TaskActorType.emptyTaskType(identifier));
        this.taskList = taskList;
    }

    @Override
    public AbstractTask getNextTaskByResult(TaskResult result) {
        return getNexTaskByResultAndContext(result, taskList);
    }

    @Override
    public void forEachTask(Consumer<AbstractTask> consumer) {
        consumer.accept(this);
        taskList.forEach(abstractTask -> abstractTask.forEachTask(consumer));
    }

    protected abstract AbstractTask getNexTaskByResultAndContext(TaskResult result, List<AbstractTask> taskList);

}
