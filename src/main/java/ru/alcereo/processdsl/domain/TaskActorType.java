package ru.alcereo.processdsl.domain;

import akka.actor.Props;
import ru.alcereo.processdsl.task.EmptyTaskActor;

import java.util.UUID;

public interface TaskActorType {
    Props getTaskActorProps();

    static TaskActorType emptyTaskType(UUID taskUuid){
        return EmptyTaskActor.getType(taskUuid);
    }
}
