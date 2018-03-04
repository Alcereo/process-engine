package ru.alcereo.processdsl.task;


import akka.persistence.fsm.AbstractPersistentFSM;
import akka.persistence.fsm.PersistentFSM;
import lombok.AllArgsConstructor;
import lombok.Value;
import scala.reflect.ClassTag;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static ru.alcereo.processdsl.task.PersistFSMTask.TaskState.ERROR_FINISHED;
import static ru.alcereo.processdsl.task.PersistFSMTask.TaskState.SUCCESS_FINISHED;

/**
 * Created by alcereo on 03.01.18.
 */
public abstract class PersistFSMTask extends AbstractPersistentFSM<PersistFSMTask.TaskState, PersistFSMTask.TaskDataState, PersistFSMTask.TaskEvents> {

    private final String persistentId;
    private final UUID taskIdentifier;

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
    public Class<TaskEvents> domainEventClass() {
        return TaskEvents.class;
    }

    /**========================================*
     *                INITIAL                  *
     *=========================================*/


    @Override
    public TaskDataState applyEvent(TaskEvents domainEvent, TaskDataState currentData) {
        if (domainEvent instanceof ContextSetEvt){
            return currentData.setProperties(((ContextSetEvt) domainEvent).properties);
        }else if (domainEvent instanceof SuccessExecutedEvt){
            return currentData;
        }
        throw new RuntimeException("Unhandled event: "+domainEvent.getClass().getName());
    }

    public PersistFSMTask(UUID taskUid) {
        this.persistentId = "task-"+taskUid;
        this.taskIdentifier = taskUid;

        log().debug("Create task with id: {}", taskUid);

        startWith(TaskState.NEW, TaskDataState.buildEmpty());

        when(TaskState.NEW,
                matchEvent(SetContextCmd.class,             this::handleCommand)    // Command ===
                    .event(AppendToContextCmd.class,        this::handleCommand)    // Command ===
                    .event(StartExecutingCmd.class,         this::handleCommand)    // Command ===
                    .event(GetStateDataQuery.class,         this::handleQuery)      // Query   ---
                    .event(GetTaskStateQuery.class,         this::handleQuery)      // Query   ---
        );

        when(TaskState.EXECUTED,
                matchEvent(SuccessFinishExecutingCmd.class, this::handleCommand)    // Command ===
                    .event(ErrorExecutingCmd.class,         this::handleCommand)    // Command ===
                    .event(AppendToContextCmd.class,        this::handleCommand)    // Command ===
                    .event(SetContextCmd.class,             this::handleCommand)    // Command ===
                    .event(GetStateDataQuery.class,         this::handleQuery)      // Query   ---
                    .event(GetTaskStateQuery.class,         this::handleQuery)      // Query   ---
        );

        when(SUCCESS_FINISHED,
                matchEvent(GetStateDataQuery.class,         this::handleQuery)      // Query   ---
                    .event(GetTaskStateQuery.class,         this::handleQuery)      // Query   ---
        );

        when(ERROR_FINISHED,
                matchEvent(GetStateDataQuery.class,         this::handleQuery)      // Query   ---
                    .event(GetTaskStateQuery.class,         this::handleQuery)      // Query   ---
        );

    }

    /**========================================*
     *                HANDLERS                 *
     *=========================================*/

    /**---            QUERIES             -----*/

    private State handleQuery(GetStateDataQuery query, TaskDataState taskDataState) {
        return stay()
                .replying(taskDataState);
    }

    private State handleQuery(GetTaskStateQuery query, TaskDataState taskDataState) {
        return stay()
                .replying(stateName());
    }

    /**---            COMMANDS             -----*/

    private State handleCommand(SetContextCmd cmd, TaskDataState taskDataState) {

            ContextSetEvt evt = new ContextSetEvt(
                    cmd.properties
            );

            return stay()
                    .applying(evt)
                    .replying(new CmdSuccess());
    }


    private State handleCommand(AppendToContextCmd cmd, TaskDataState taskDataState) {

        Map<String, Object> properties = new HashMap<>(taskDataState.getProperties());
        properties.putAll(cmd.properties);

        ContextSetEvt evt = new ContextSetEvt(
                properties
        );

        return stay()
                .applying(evt)
                .replying(new CmdSuccess());
    }



    private State handleCommand(StartExecutingCmd cmd, TaskDataState taskDataState) {

        return goTo(TaskState.EXECUTED)
                .replying(new CmdSuccess())
                .andThen(exec(this::handleExecution));
    }


    private State handleCommand(SuccessFinishExecutingCmd cmd, TaskDataState taskDataState) {

        log().debug("Hande command to success finis task. TaskId {}", taskIdentifier);

        return goTo(SUCCESS_FINISHED)
                .replying(new CmdSuccess())
                .andThen(exec(
                        taskDataState1 ->
                                getContext()
                                        .getParent()
                                        .tell(new SuccessExecutedEvt(taskIdentifier, taskDataState.getProperties()), getSelf())
                ));
    }

    private State handleCommand(ErrorExecutingCmd cmd, TaskDataState taskDataState) {

        return goTo(TaskState.ERROR_FINISHED)
                .replying(new CmdSuccess())
                .andThen(exec(taskDataState1 ->
                        getContext().getParent().tell(
                                new ExecutedWithErrorsEvt(
                                        taskIdentifier,
                                        cmd.getError(),
                                        taskDataState.getProperties()
                                ), getSelf())
                ));
    }

    /**---            ABSTRACTS             -----*/

    public abstract void handleExecution(TaskDataState taskStateData);


    /**========================================*
     *                STATE                    *
     *=========================================*/

    @Value
    @AllArgsConstructor
    public static class TaskDataState{
        Map<String, Object> properties;

        TaskDataState setProperties(Map<String, Object> properties){
            return new TaskDataState(properties);
        }

        static TaskDataState buildEmpty(){
            return new TaskDataState(new HashMap<>());
        }
    }

    public enum TaskState implements PersistentFSM.FSMState{

        NEW("New task"),
        EXECUTED("Start execution"),
        SUCCESS_FINISHED("Task finished success"),
        ERROR_FINISHED("Task finished with errors");

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
     *                COMMANDS                 *
     *=========================================*/

    public interface Command {
    }

    @Value
    public static final class SetContextCmd implements Command{
        Map<String, Object> properties;
    }

    @Value
    public static final class AppendToContextCmd implements Command{
        Map<String, Object> properties;
    }

    @Value
    public static final class StartExecutingCmd implements Command{}


    @Value
    public static final class SuccessFinishExecutingCmd implements Command {}


    @Value
    public static final class ErrorExecutingCmd implements Command {
        Throwable error;
    }

    @Value
    public static final class CmdSuccess implements Command{}

    @Value
    public static final class CmdFail implements Command{
        Throwable error;
    }


    /**========================================*
     *                QUERIES                  *
     *=========================================*/

    public interface Query {}

    @Value
    public static final class GetStateDataQuery implements Query {}

    /**
     * return {@link TaskState} class
     */
    @Value
    public static final class GetTaskStateQuery implements Query {}

    /**========================================*
     *                EVENTS                   *
     *=========================================*/

    public interface TaskEvents extends Serializable {}

    @Value
    public static final class ContextSetEvt implements TaskEvents{
        Map<String, Object> properties;
    }

    @Value
    public static final class SuccessExecutedEvt implements TaskEvents{
        UUID taskUid;
        Map<String, Object> properties;
    }

    @Value
    public static final class ExecutedWithErrorsEvt implements TaskEvents{
        UUID taskUid;
        Throwable error;
        Map<String, Object> properties;
    }

    @Value
    public static final class ExecutionStarted implements TaskEvents{}

}
