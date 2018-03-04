package ru.alcereo.processdsl.domain.task;

import lombok.*;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.io.Serializable;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

//    TODO: Сделать иммутабельным!
@Data
@EqualsAndHashCode(of = {"identifier"})
public abstract class AbstractTask implements Serializable {

    @Getter
    @NonNull
    protected final UUID identifier;

    @NonNull
    protected Map<String, Object> properties;

    @NonNull
    protected PropertiesExchangeData propertiesExchangeData;

    @NonNull
    protected TaskActorType type;


    public void acceptDataToStart(TaskResult previousTaskResult, Map<String, Object> parentContext) {
        val properties = getProperties();
        Map<String, Object> resultProperties = previousTaskResult.getResultProperties();

        for (PropertiesExchangeData.PropMappingData propTuple : getPropertiesExchangeData().getInnerPropsFromLastOutput()) {
            properties.put(
                    propTuple.getInnerProp(),
                    resultProperties.get(propTuple.getOuterProp())
            );
        }

        for (PropertiesExchangeData.PropMappingData propTuple : getPropertiesExchangeData().getInnerPropsFromContext()) {
            properties.put(
                    propTuple.getInnerProp(),
                    parentContext.get(propTuple.getOuterProp())
            );
        }
    }

    public abstract AbstractTask getNextTaskByResult(final TaskResult result);

    public abstract void forEachTask(Consumer<AbstractTask> consumer);

    /**========================================*
     *                 RESULT                  *
     *=========================================*/

    public interface TaskResult{
        boolean isSuccess();
        UUID getIdentifier();
        Map<String, Object> getResultProperties();
    }

    @Value
    public static class SuccessTaskResult implements TaskResult{
        UUID identifier;
        Map<String, Object> resultProperties;

        @Override
        public boolean isSuccess() {
            return true;
        }

        @Override
        public Map<String, Object> getResultProperties() {
            return resultProperties;
        }
    }

    @Value
    public static class FailureTaskResult implements TaskResult{
        UUID identifier;
        Throwable exception;
        Map<String, Object> resultProperties;

        @Override
        public boolean isSuccess() {
            return false;
        }

        @Override
        public Map<String, Object> getResultProperties() {
            return resultProperties;
        }
    }

}
