package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import lombok.NonNull;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;


public class OneDirectionTask extends AbstractTask {

    private final AbstractTask nextTask;

    @Builder
    public OneDirectionTask(UUID identifier,
                            Map<String, Object> properties,
                            PropertiesExchangeData propertiesExchangeData,
                            TaskActorType type,
                            @NonNull AbstractTask nextTask) {
        super(identifier, properties, propertiesExchangeData, type);
        this.nextTask = nextTask;
    }

    @Override
    public AbstractTask getNextTaskByResult(TaskResult result) {
        return nextTask;
    }

    @Override
    public void forEachTask(Consumer<AbstractTask> consumer) {
        consumer.accept(this);
        nextTask.forEachTask(consumer);
    }
}
