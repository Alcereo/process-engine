package ru.alcereo.processdsl.domain;

import akka.actor.ActorRef;
import lombok.*;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.Option;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Data
@EqualsAndHashCode(of = "identifier")
@RequiredArgsConstructor
public class BusinessProcess implements Serializable{

    final UUID identifier;

    @Getter(value = AccessLevel.PRIVATE)
    ArrayList<Task> tasks = new ArrayList<>();
    Map<String, Object> processContext = new HashMap<>();

    List<BusinessEvent> raisedBusinessEvents = new ArrayList<>();

    /**========================================*
     *               METODS                    *
     *=========================================*/

    public void addLastTask(Task task){
        raisedBusinessEvents.add(new LastTaskAddedEvt(task));
    }

    public Task getFirstTask() {
        return tasks.get(0);
    }

    public boolean containsTaskIdentifier(UUID identifier) {
        return tasks.stream().anyMatch(context -> context.getIdentifier().equals(identifier));
    }

    /**========================================*
     *         EVENTS SOURCED HANDLERS         *
     *=========================================*/

    public void handleEvent(BusinessEvent businessEvent){
        businessEvent.handleByProcess(this);
    }

    public Optional<Task> getNextTaskAfter(UUID taskUid) {
        return tasks.stream()
                .filter(task -> task.getIdentifier().equals(taskUid))
                .findFirst()
                .map(task -> tasks.indexOf(task))
                .filter(integer -> integer!=-1)
                .map(integer -> {
                    if (tasks.size()<integer+2)
                        return null;
                    else
                        return tasks.get(integer+1);
                });
    }

    public Boolean isLastTask(UUID taskUid) {
        return tasks.stream()
                .filter(task -> task.getIdentifier().equals(taskUid))
                .findFirst()
                .map(task -> tasks.indexOf(task))
                .map(integer -> tasks.size()==integer+1)
                .orElse(false);
    }

    /**========================================*
     *                EVENTS                   *
     *=========================================*/

    public interface BusinessEvent extends Serializable {
        void handleByProcess(BusinessProcess process);
    }

    @Value
    public static class LastTaskAddedEvt implements BusinessEvent {
        public Task task;


        @Override
        public void handleByProcess(BusinessProcess process) {
            process.getTasks().add(task);
        }
    }

    @Value
    public static class PassedReadyEvt implements BusinessEvent {
        @Override
        public void handleByProcess(BusinessProcess process) {
        }
    }

    @Value
    public static class ContextSetEvt implements BusinessEvent {
        public Map<String, Object> context;

        @Override
        public void handleByProcess(BusinessProcess process) {
            process.setProcessContext(context);
        }
    }


    /**========================================*
     *               CORRUPTION                *
     *=========================================*/

//        util func


    public void setTaskState(UUID identifier, PersistFSMTask.TaskState taskState) {
        tasksStatuses.put(identifier, taskState);
    }

    public Option<PersistFSMTask.TaskState> taskState(UUID identifier){
        return Option.apply(tasksStatuses.get(identifier));
    }

    /**========================================*
     *                 Actor legacy            *
     *=========================================*/

    final Map<UUID, PersistFSMTask.TaskState> tasksStatuses = new HashMap<>();
    final Map<ActorRef, Task> childTaskActorsCache = new HashMap<>();

    public Option<UUID> getIdentifierByActorRef(ActorRef ref){
        return Option.apply(childTaskActorsCache.get(ref))
                .map(Task::getIdentifier);
    }

    public Option<ActorRef> getActorRefByIdentifier(UUID identifier) {
        return Option.apply(childTaskActorsCache
                .entrySet().stream()
                .filter(entry -> entry.getValue().getIdentifier().equals(identifier))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null));
    }

    public List<ActorRef> getTaskRefs() {
        return tasks.stream()
                .map(Task::getIdentifier)
                .map(this::getActorRefByIdentifier)
                .map(actorRefOption -> actorRefOption.fold(() -> null, v1 -> v1))
                .collect(Collectors.toList());
    }

}
