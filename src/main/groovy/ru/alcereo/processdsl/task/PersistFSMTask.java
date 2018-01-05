package ru.alcereo.processdsl.task;

import akka.japi.pf.FI;
import akka.persistence.fsm.AbstractPersistentFSM;
import akka.persistence.fsm.PersistentFSM;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by alcereo on 03.01.18.
 */
public abstract class PersistFSMTask extends AbstractPersistentFSM<PersistFSMTask.TaskState, PersistFSMTask.TaskStateData, PersistFSMTask.TaskEvents> {

    private final String persistentId;

//    public static Props props(String persistenceId) {
//        return Props.create(PersistFSMTask.class, () -> new PersistFSMTask(persistenceId));
//    }

    @Override
    public String persistenceId() {
        return this.persistentId;
    }


    @Override
    public TaskStateData applyEvent(TaskEvents domainEvent, TaskStateData currentData) {
        if (domainEvent instanceof PreparedEvt){
            currentData.properties = ((PreparedEvt) domainEvent).properties;
            return currentData;
        }else if (domainEvent instanceof SuccessExecutedEvt){
            return currentData;
        }
        throw new RuntimeException("Unhandled event: "+domainEvent.getClass().getName());
    }

    public PersistFSMTask(String persistentId) {
        this.persistentId = persistentId;


        startWith(TaskState.NEW, new TaskStateData());

        FI.Apply2<GetStateDataCmd, TaskStateData, State<TaskState, TaskStateData, TaskEvents>> getStateDataApply =
                (getStateDataCmd, taskStateData) -> stay().replying(taskStateData).andThen(exec(this::handleGetState));

        FI.Apply2<GetStateCmd, TaskStateData, State<TaskState, TaskStateData, TaskEvents>> getStateApply =
                (getStateDataCmd, taskStateData) -> stay().replying(stateName()).andThen(exec(this::handleGetState));

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
                .event(GetStateCmd.class, getStateApply)
        );

        when(TaskState.PREPARED,
                matchEvent(ExecuteCmd.class, (executeCmd, taskStateData) ->
                    goTo(TaskState.EXECUTED)
                        .andThen(exec(this::handleExecution))
                ).event(GetStateDataCmd.class, getStateDataApply)
                .event(GetStateCmd.class, getStateApply)
        );

    }

    abstract void handleGetState(TaskStateData taskStateData);

    abstract void handleExecution(TaskStateData taskStateData);

    abstract void handlePrepare(TaskStateData taskStateData);

//    State

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

    @Data
    static class TaskStateData {
        Map<String, Object> properties = new HashMap<>();
    }

//    Commands

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
    public static final class GetStateCmd implements Command{}

//    Events
    @Override
    public Class<TaskEvents> domainEventClass() {
        return TaskEvents.class;
    }

    interface TaskEvents extends Serializable {}

    @Value
    public static final class PreparedEvt implements TaskEvents{
        Map<String, Object> properties;
    }

    @Value
    public static final class SuccessExecutedEvt implements TaskEvents{
    }


}
