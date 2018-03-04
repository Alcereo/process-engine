package ru.alcereo.processdsl.domain.task;

import com.sun.istack.internal.NotNull;
import lombok.Builder;
import lombok.val;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static ru.alcereo.processdsl.domain.task.PropertiesExchangeData.PropMappingData;


public class OneWayTask extends AbstractTask {

    @NotNull
    private final AbstractTask nextTask;

    @Builder
    private OneWayTask(UUID identifier,
                      Map<String, Object> properties,
                      PropertiesExchangeData propertiesExchangeData,
                      TaskActorType type,
                      AbstractTask nextTask) {

        super(identifier, properties, propertiesExchangeData, type);
        this.nextTask = nextTask;
    }


    @Override
    public void acceptDataToStart(TaskResult previousTaskResult, Map<String, Object> parentContext) {
        val properties = getProperties();
        Map<String, Object> resultProperties = previousTaskResult.getResultProperties();

        for (PropMappingData propTuple : getPropertiesExchangeData().getInnerPropsFromLastOutput()) {
            properties.put(
                    propTuple.getInnerProp(),
                    resultProperties.get(propTuple.getOuterProp())
            );
        }

        for (PropMappingData propTuple : getPropertiesExchangeData().getInnerPropsFromContext()) {
            properties.put(
                    propTuple.getInnerProp(),
                    parentContext.get(propTuple.getOuterProp())
            );
        }
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
