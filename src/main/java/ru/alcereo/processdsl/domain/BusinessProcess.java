package ru.alcereo.processdsl.domain;

import lombok.*;
import ru.alcereo.processdsl.domain.task.AbstractTask;
import ru.alcereo.processdsl.domain.task.ProcessResultTask;
import ru.alcereo.processdsl.domain.task.PropertiesExchangeData;

import java.io.Serializable;
import java.util.*;

@Data
@EqualsAndHashCode(of = "identifier")
public class BusinessProcess implements Serializable{

    final UUID identifier;

    AbstractTask headerTask;

    private AbstractTask currentTask;

    Map<String, Object> processContext;
    private boolean isFinished = false;
    private boolean isSuccess = false;

    @Builder
    public BusinessProcess(@NonNull UUID identifier,
                           AbstractTask headerTask,
                           @NonNull Map<String, Object> processContext) {

        this.identifier = identifier;
        this.headerTask = headerTask;
        this.currentTask = headerTask;
        this.processContext = new HashMap<>(processContext);
        updateHeaderTaskProperties(headerTask, processContext);
    }

    /**========================================*
     *               METODS                    *
     *=========================================*/

    public void setHeaderTask(@NonNull AbstractTask headerTask){
        this.headerTask = headerTask;
        this.currentTask = headerTask;
        updateHeaderTaskProperties(headerTask, processContext);
    }

    public List<AbstractTask> getAllTasks(){
        List<AbstractTask> tasks = new ArrayList<>();
        headerTask.forEachTask(tasks::add);
        return tasks;
    }

    public AbstractTask getCurrentTask(){
        return currentTask;
    }

    public boolean containsTaskIdentifier(UUID identifier){
        AbstractTask[] finderTask = new AbstractTask[1];
        headerTask.forEachTask(abstractTask -> {
            if (abstractTask.getIdentifier().equals(identifier))
                finderTask[0] = abstractTask;
        });

        return finderTask[0] != null;
    }

    public void acceptCurrentTaskResult(AbstractTask.TaskResult result) throws AcceptResultOnFinishException {
        if (!result.getIdentifier().equals(currentTask.getIdentifier()))
            throw new RuntimeException("Result not belong to current task");

        if (this.isFinished)
            throw new AcceptResultOnFinishException();

        appendPropertiesFromTaskToContext(currentTask,result, getProcessContext());

        AbstractTask nextTaskByResult = currentTask.getNextTaskByResult(result);

        if (nextTaskByResult instanceof ProcessResultTask) {
            this.isFinished = true;
            isSuccess = ((ProcessResultTask) nextTaskByResult).isSuccess();
        }else
            nextTaskByResult.acceptDataToStart(
                    result,
                    getProcessContext()
            );

        this.currentTask = nextTaskByResult;
    }

    public boolean isFinished() {
        return this.isFinished;
    }

    /**========================================*
     *                Utils                    *
     *=========================================*/

    private static void updateHeaderTaskProperties(AbstractTask headerTask, Map<String, Object> processContext) {
        if (headerTask!=null)
            headerTask.acceptDataToStart(processContext);
    }

    private void appendPropertiesFromTaskToContext(AbstractTask currentTask, AbstractTask.TaskResult result, Map<String, Object> processContext) {

        Map<String, Object> resultProperties = result.getResultProperties();

        for (PropertiesExchangeData.PropMappingData propMappingData : currentTask.getPropertiesExchangeData().getOuterPropsToContext()) {
            processContext.put(propMappingData.getOuterProp(), resultProperties.get(propMappingData.getInnerProp()));
        }
    }

    /**========================================*
     *                EVENTS                   *
     *=========================================*/

    public interface BusinessEvent extends Serializable {
    }

    @Value
    public static class LastTaskAddedEvt implements BusinessEvent {
        public AbstractTask task;
    }

    @Value
    public static class PassedReadyEvt implements BusinessEvent {
    }

    @Value
    public static class ContextSetEvt implements BusinessEvent {
        public Map<String, Object> context;
    }

    @Value
    public static class ProcessCreatedEvt implements BusinessEvent {
        UUID uuid;
    }

    @Value
    public static class ProcessStartedEvt implements BusinessEvent {
        UUID uuid;
    }

    @Value
    public static class ProcessFinishedEvt implements BusinessEvent {
        UUID uuid;
        boolean isSuccess;
    }

}
