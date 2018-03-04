package ru.alcereo.processdsl.domain;

import lombok.*;
import ru.alcereo.processdsl.domain.task.AbstractTask;
import ru.alcereo.processdsl.domain.task.ProcessResultTask;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.Option;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.Serializable;
import java.util.*;

@Data
@EqualsAndHashCode(of = "identifier")
public class BusinessProcess implements Serializable{

    final UUID identifier;

    final AbstractTask headerTask;

    private AbstractTask currentTask;

    Map<String, Object> processContext;
    private boolean isFinished = false;

    @Builder
    public BusinessProcess(@NonNull UUID identifier,
                           @NonNull AbstractTask headerTask,
                           @NonNull Map<String, Object> processContext) {

        this.identifier = identifier;
        this.headerTask = headerTask;
        this.currentTask = headerTask;
        this.processContext = processContext;
    }

    /**========================================*
     *               METODS                    *
     *=========================================*/

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

    public void acceptCurrentTaskResult(AbstractTask.SuccessTaskResult result) throws AcceptResultOnFinish {
        if (!result.getIdentifier().equals(currentTask.getIdentifier()))
            throw new RuntimeException("Result not belong to current task");

        if (this.isFinished)
            throw new AcceptResultOnFinish();

        AbstractTask nextTaskByResult = currentTask.getNextTaskByResult(result);

        if (nextTaskByResult instanceof ProcessResultTask)
            this.isFinished = true;
        else
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
     *         EVENTS SOURCED HANDLERS         *
     *=========================================*/

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
    }

    /**========================================*
     *               CORRUPTION                *
     *=========================================*/

    public void addLastTask(AbstractTask task){
//        raisedBusinessEvents.add(new LastTaskAddedEvt(task));
        throw new NotImplementedException();
    }

    public AbstractTask getFirstTask() {
        throw new NotImplementedException();
    }

    public void setTaskState(UUID identifier, PersistFSMTask.TaskState taskState) {
        throw new NotImplementedException();
//        tasksStatuses.put(identifier, taskState);
    }

    public Option<PersistFSMTask.TaskState> taskState(UUID identifier){
        throw new NotImplementedException();
//        return Option.apply(tasksStatuses.get(identifier));
    }

    public Optional<AbstractTask> getNextTaskAfter(UUID taskUid) {
//        return tasks.stream()
//                .filter(task -> task.getIdentifier().equals(taskUid))
//                .findFirst()
//                .map(task -> tasks.indexOf(task))
//                .filter(integer -> integer!=-1)
//                .map(integer -> {
//                    if (tasks.size()<integer+2)
//                        return null;
//                    else
//                        return tasks.get(integer+1);
//                });

        throw new NotImplementedException();
    }

    public Boolean isLastTask(UUID taskUid) {
//        return tasks.stream()
//                .filter(task -> task.getIdentifier().equals(taskUid))
//                .findFirst()
//                .map(task -> tasks.indexOf(task))
//                .map(integer -> tasks.size()==integer+1)
//                .orElse(false);

        throw new NotImplementedException();
    }

    public void acceptTaskResult(AbstractTask.TaskResult taskResult) {
        throw new NotImplementedException();
    }

    /**========================================*
     *                 Actor legacy            *
     *=========================================*/

//    final Map<UUID, PersistFSMTask.TaskState> tasksStatuses = new HashMap<>();
//    final Map<ActorRef, AbstractTask> childTaskActorsCache = new HashMap<>();
//
//    public Option<UUID> getIdentifierByActorRef(ActorRef ref){
//        return Option.apply(childTaskActorsCache.get(ref))
//                .map(AbstractTask::getIdentifier);
//    }
//
//    public Option<ActorRef> getActorRefByIdentifier(UUID identifier) {
//        return Option.apply(childTaskActorsCache
//                .entrySet().stream()
//                .filter(entry -> entry.getValue().getIdentifier().equals(identifier))
//                .map(Map.Entry::getKey)
//                .findFirst().orElse(null));
//    }
//
//    public List<ActorRef> getTaskRefs() {
////        return tasks.stream()
////                .map(AbstractTask::getIdentifier)
////                .map(this::getActorRefByIdentifier)
////                .map(actorRefOption -> actorRefOption.fold(() -> null, v1 -> v1))
////                .collect(Collectors.toList());
//        throw new NotImplementedException();
//    }



}
