package ru.alcereo.processdsl.task;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;
import ru.alcereo.processdsl.domain.TaskActorType;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;

public class TestPrintTaskActor extends PersistFSMTask{

    private final String propertyToPrint;

    public TestPrintTaskActor(UUID taskIdentifier, String propertyToPrint) {
        super(taskIdentifier);
        this.propertyToPrint = propertyToPrint;
    }

    public static TaskActorType getType(UUID taskIdentifier, String propertyToPrint){
        return () -> Props.create(
                TestPrintTaskActor.class,
                () -> new TestPrintTaskActor(taskIdentifier, propertyToPrint)
        );
    }

    @Override
    public void handleExecution(TaskDataState taskStateData) {

        ActorRef initSender = getSender();

        System.out.println("=============================================");
        System.out.println("=================PRINT-ACTOR=================");
        System.out.println();

        System.out.println(taskStateData.getProperties().get(propertyToPrint));

        System.out.println();
        System.out.println("=============================================");
        System.out.println("=============================================");

        ExecutionContext ds = getContext().dispatcher();

        Future<Object> sendSuccessF = Patterns.ask(
                getSelf(),
                new SuccessFinishExecutingCmd(),
                Timeout.apply(3, TimeUnit.SECONDS)
        );

        sendSuccessF.onFailure(
                failure(throwable -> {
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
}
