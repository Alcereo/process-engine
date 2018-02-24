package ru.alcereo.processdsl.domain;

import akka.actor.ActorRef;
import lombok.Data;
import lombok.Value;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.Option;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class Process implements Serializable{

    ArrayList<Task> tasks = new ArrayList<>();
    Map<String, Object> processContext = new HashMap<>();

//        util func

    public void addLastTask(Task task){
        tasks.add(task);
    }

    public void setTaskState(UUID identifier, PersistFSMTask.TaskState taskState) {
        tasksStatuses.put(identifier, taskState);
    }

    public List<Task> getTasks() {
        return tasks;
    }

    public boolean containsIdentifier(UUID identifier) {
        return tasks.stream().anyMatch(context -> context.getIdentifier().equals(identifier));
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

    /**========================================*
     *                 EVENTS                  *
     *=========================================*/

    public interface Events extends Serializable {}

    @Value
    public static class LastTaskAddedEvt implements Events {
        public Task task;
    }

    @Value
    public static class PassedReadyEvt implements Events {}

    @Value
    public static class ContextSetEvt implements Events {
        public Map<String, Object> context;
    }

    /**========================================*
     *         EVENTS SOURCED HANDLERS         *
     *=========================================*/

    public void handleEvent(LastTaskAddedEvt event){
        addLastTask(event.task);
    }

    public void handleEvent(PassedReadyEvt event){}

    public void handleEvent(ContextSetEvt event){
        setProcessContext(event.context);
    }


}
