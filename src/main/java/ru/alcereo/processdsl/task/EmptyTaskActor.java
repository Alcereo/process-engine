package ru.alcereo.processdsl.task;

import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import ru.alcereo.processdsl.domain.TaskActorType;
import scala.concurrent.ExecutionContext;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;

public class EmptyTaskActor extends PersistFSMTask {
    public EmptyTaskActor(UUID taskUid) {
        super(taskUid);
    }

    @Override
    public void handleExecution(TaskDataState taskStateData) {
        ExecutionContext ds = getContext().dispatcher();
        Patterns.ask(
                getSelf(),
                new SuccessFinishExecutingCmd(),
                Timeout.apply(5, TimeUnit.SECONDS)
        ).onFailure(failure(throwable -> {
            log().error(throwable, "Error execution task!!");

            Patterns.ask(
                    getSelf(),
                    new ErrorExecutingCmd(throwable),
                    Timeout.apply(5, TimeUnit.SECONDS)
            ).onFailure(
                    failure(throwable1 -> {
                        throw new RuntimeException("Error execution!!");
                    }), ds
            );
        }), ds);
    }

    public static TaskActorType getType(UUID taskUuid){
        return () -> Props.create(EmptyTaskActor.class, () -> new EmptyTaskActor(taskUuid));
    }
}
