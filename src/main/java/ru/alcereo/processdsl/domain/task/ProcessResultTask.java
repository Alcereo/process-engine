package ru.alcereo.processdsl.domain.task;

import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class ProcessResultTask extends AbstractTask {


    public ProcessResultTask(UUID identifier, Map<String, Object> properties, PropertiesExchangeData propertiesExchangeData, TaskActorType type) {
        super(identifier, properties, propertiesExchangeData, type);
    }

    @Override
    public void acceptDataToStart(TaskResult previousTaskResult, Map<String, Object> parentContext) {
    }

    @Override
    public AbstractTask getNextTaskByResult(TaskResult result) {
        return null;
    }

    @Override
    public void forEachTask(Consumer<AbstractTask> consumer) {
        consumer.accept(this);
    }

    public abstract boolean isSuccess();
}
