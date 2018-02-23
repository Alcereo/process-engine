package ru.alcereo.processdsl.domain;

import akka.actor.ActorRef;
import lombok.Data;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.Option;

import java.util.*;
import java.util.stream.Collectors;

@Data
public class Process {
    private ArrayList<Task> taskContextList = new ArrayList<>();
    private final Map<UUID, PersistFSMTask.TaskState> tasksStatuses = new HashMap<>();
    private final Map<ActorRef, Task> childTaskActorsCache = new HashMap<>();
    private Map<String, Object> processContext = new HashMap<>();

//        util func

    public void addLastTask(Task taskContext){
        taskContextList.add(taskContext);
    }

    public Option<UUID> getIdentifierByActorRef(ActorRef ref){
        return Option.apply(childTaskActorsCache.get(ref))
                .map(taskContext -> taskContext.getIdentifier());
    }

    public Option<ActorRef> getActorRefByIdentifier(UUID identifier) {
        return Option.apply(childTaskActorsCache
                .entrySet().stream()
                .filter(entry -> entry.getValue().getIdentifier().equals(identifier))
                .map(Map.Entry::getKey)
                .findFirst().orElse(null));
    }

    public void setTaskState(UUID identifier, PersistFSMTask.TaskState taskState) {
        tasksStatuses.put(identifier, taskState);
    }

    public List<Task> getTasksContexts() {
        return taskContextList;
    }

    public boolean containsIdentifier(UUID identifier) {
        return taskContextList.stream().anyMatch(context -> context.getIdentifier().equals(identifier));
    }

    public Option<PersistFSMTask.TaskState> taskState(UUID identifier){
        return Option.apply(tasksStatuses.get(identifier));
    }

    public List<ActorRef> getTaskRefs() {
        return taskContextList.stream()
                .map(Task::getIdentifier)
                .map(this::getActorRefByIdentifier)
                .map(actorRefOption -> actorRefOption.fold(() -> null, v1 -> v1))
                .collect(Collectors.toList());
    }
}
