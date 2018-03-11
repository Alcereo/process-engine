package ru.alcereo.processdsl.domain;

import akka.actor.Props;
import ru.alcereo.processdsl.write.task.EmptyTaskActor;

import java.util.UUID;

public interface TaskActorType {
    Props getTaskActorProps();

    static TaskActorType emptyTaskType(UUID taskUuid){
        return EmptyTaskActor.getType(taskUuid);
    }
}
