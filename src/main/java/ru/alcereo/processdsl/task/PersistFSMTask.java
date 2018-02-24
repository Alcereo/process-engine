package ru.alcereo.processdsl.task;


import akka.dispatch.OnComplete;
import akka.dispatch.Recover;
import akka.japi.pf.FI;
import akka.pattern.Patterns;
import akka.persistence.fsm.AbstractPersistentFSM;
import akka.persistence.fsm.PersistentFSM;
import lombok.Value;
import ru.alcereo.processdsl.domain.Task;
import scala.compat.java8.FutureConverters;
import scala.concurrent.ExecutionContextExecutor;
import scala.concurrent.Future;
import scala.reflect.ClassTag;

import java.io.Serializable;
import java.util.Map;

import static ru.alcereo.processdsl.task.PersistFSMTask.TaskState.FINISHED;

/**
 * Created by alcereo on 03.01.18.
 */
public abstract class PersistFSMTask<TASK extends Task> extends AbstractPersistentFSM<PersistFSMTask.TaskState, TASK, PersistFSMTask.TaskEvents> {

    private final String persistentId;

//    public static Props props(String persistenceId) {
//        return Props.create(PersistFSMTask.class, () -> new PersistFSMTask(persistenceId));
//    }

    /**
     * Нужно тут тупо из-за бага Idea 2017
     * Компилируется и без этого
     */
    @Override
    @SuppressWarnings("unchecked")
    public ClassTag domainEventTag() {
        return super.domainEventTag();
    }

    /**
     * Нужно тут тупо из-за бага Idea 2017
     * Компилируется и без этого
     */
    @Override
    @SuppressWarnings("unchecked")
    public scala.collection.immutable.Map statesMap() {
        return super.statesMap();
    }


    @Override
    public String persistenceId() {
        return this.persistentId;
    }


    @Override
    public TASK applyEvent(TaskEvents domainEvent, TASK currentData) {
        if (domainEvent instanceof PreparedEvt){
            return currentData.setProperties(((PreparedEvt) domainEvent).properties);
        }else if (domainEvent instanceof SuccessExecutedEvt){
            return currentData;
        }
        throw new RuntimeException("Unhandled event: "+domainEvent.getClass().getName());
    }

    public PersistFSMTask(String persistentId, TASK task) {
        this.persistentId = persistentId;


        startWith(TaskState.NEW, task);

        FI.Apply2<GetStateDataCmd, TASK, State<TaskState, TASK, TaskEvents>> getStateDataApply =
                (getStateDataCmd, taskStateData) -> stay().replying(taskStateData);

        FI.Apply2<GetTaskStateCmd, TASK, State<TaskState, TASK, TaskEvents>> getStateApply =
                (getStateDataCmd, taskStateData) -> stay().replying(stateName());

        when(TaskState.NEW,
                matchEvent(PrepareCmd.class, (prepareCmd, taskStateData) -> {
                    PreparedEvt evt = new PreparedEvt(
                            prepareCmd.properties
                    );
                    return goTo(TaskState.PREPARED)
                            .applying(evt)
                            .andThen(exec(this::handlePrepare))
                            .replying(evt);

                }).event(GetStateDataCmd.class, getStateDataApply)
                .event(GetTaskStateCmd.class, getStateApply)
        );

        when(TaskState.PREPARED,
                matchEvent(ExecuteCmd.class, (executeCmd, taskStateData) ->
                    goTo(TaskState.EXECUTED)
                        .replying(new ExecutionStarted())
                        .andThen(exec(this::handleExecution))
                ).event(GetStateDataCmd.class, getStateDataApply)
                .event(GetTaskStateCmd.class, getStateApply)

        );

        when(TaskState.EXECUTED,
                matchEvent(GetStateDataCmd.class, getStateDataApply)
                    .event(GetTaskStateCmd.class, getStateApply)
                    .event(ExecutingSuccessFinish.class, (event, taskStateData) -> {
                        return goTo(FINISHED)
                                .replying(event);
                    })
        );

        when(FINISHED,
                matchEvent(GetStateDataCmd.class, getStateDataApply)
                        .event(GetTaskStateCmd.class, getStateApply)
        );

    }

    /**========================================*
     *                 HANDLERS                *
     *=========================================*/

    public void handleExecution(TASK task){

        ExecutionContextExecutor ds = getContext().getSystem().dispatcher();


        final Future<TaskEvents> executionResultF =
                FutureConverters.toScala(task.execute());

        final Future<?> resultEvent = executionResultF
                .recover(new Recover<ExecutingFaled>() {
                    @Override
                    public ExecutingFaled recover(Throwable failure) {
                        return new ExecutingFaled(failure);
                    }
                }, ds);

        Patterns.pipe(resultEvent, ds)
                .to(getSender())
                .to(getSelf());

    }

    public void handlePrepare(TASK task){
        ExecutionContextExecutor ds = getContext().getSystem().dispatcher();

        final Future<Object> executionResultF =
                FutureConverters.toScala(task.prepare());

        executionResultF
                .onComplete(new OnComplete<Object>() {
                    @Override
                    public void onComplete(Throwable failure, Object success) throws Throwable {
                        log().error(failure, "Error prepare task!");
                    }
                }, ds);

    };

    /**========================================*
     *                 STATE                   *
     *=========================================*/

    public enum TaskState implements PersistentFSM.FSMState{

        NEW("New task"),
        PREPARED("Task prepered for execution"),
        EXECUTED("Start execution"),
        FINISHED("Task finished");

        private final String stateIdentifier;

        TaskState(String stateIdentifier) {
            this.stateIdentifier = stateIdentifier;
        }

        @Override
        public String identifier() {
            return stateIdentifier;
        }
    }

    /**========================================*
     *                 COMMANDS                *
     *=========================================*/

    public interface Command {
    }

    @Value
    public static final class PrepareCmd implements Command{
        Map<String, Object> properties;
    }

    @Value
    public static final class ExecuteCmd implements Command{}

    @Value
    public static final class GetStateDataCmd implements Command{}

    @Value
    public static final class GetTaskStateCmd implements Command{}

    /**========================================*
     *                 EVENTS                  *
     *=========================================*/

    @Override
    public Class<TaskEvents> domainEventClass() {
        return TaskEvents.class;
    }

    public interface TaskEvents extends Serializable {}

    @Value
    public static final class PreparedEvt implements TaskEvents{
        Map<String, Object> properties;
    }

    @Value
    public static final class SuccessExecutedEvt implements TaskEvents{
    }

    @Value
    public static final class ExecutionStarted implements TaskEvents{}

    @Value
    public static final class ExecutingSuccessFinish {
        Map<String, Object> result;
    }

    @Value
    public static final class ExecutingFaled {
        Throwable error;
    }
}
