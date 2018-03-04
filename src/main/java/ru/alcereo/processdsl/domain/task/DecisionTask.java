package ru.alcereo.processdsl.domain.task;

import com.sun.istack.internal.NotNull;
import lombok.val;
import ru.alcereo.processdsl.domain.TaskActorType;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public abstract class DecisionTask extends AbstractTask {

    @NotNull
    private final List<AbstractTask> taskList;

    protected DecisionTask(UUID identifier,
                         Map<String, Object> properties,
                         PropertiesExchangeData propertiesExchangeData,
                         TaskActorType type, List<AbstractTask> taskList) {

        super(identifier, properties, propertiesExchangeData, type);
        this.taskList = taskList;
    }

    @Override
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
