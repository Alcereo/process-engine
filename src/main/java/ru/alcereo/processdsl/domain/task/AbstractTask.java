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


    public abstract void acceptDataToStart(final TaskResult previousTaskResult,
                                           final Map<String, Object> parentContext);

    public abstract AbstractTask getNextTaskByResult(final TaskResult result);

    public abstract void forEachTask(Consumer<AbstractTask> consumer);

    /**========================================*
     *                 RESULT                  *
     *=========================================*/

    public interface TaskResult{
        boolean isSuccess();
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
