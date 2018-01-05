package ru.alcereo.processdsl.process;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.persistence.fsm.AbstractPersistentFSM;
import akka.persistence.fsm.PersistentFSM;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.val;
import scala.Tuple2;

import java.io.Serializable;
import java.util.*;

/**
 * Created by alcereo on 01.01.18.
 */
public class Process extends AbstractPersistentFSM<Process.State, Process.StateData, Process.Events> {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String persistenceId;

    private final Map<ActorRef, TaskExecutionContext> childTaskActorsCache = new HashMap<>();

    public static Props props(String persistenceId){
        return Props.create(Process.class, (akka.japi.Creator<Process>) () -> new Process(persistenceId));
    }

    @Override
    public String persistenceId() {
        return this.persistenceId;
    }

    @Override
    public Class<Events> domainEventClass() {
        return Events.class;
    }


    /**
     * For testing purpose only
     * @param persistenceId identifier to persist and recover data
     * @param createChild create child actor on start
     * @param childName child actor name
     */
    Process(String persistenceId, boolean createChild, String childName){
        this(persistenceId);

        if (createChild)
            getContext().actorOf(Props.empty(), childName);
    }

    private Process(String persistenceId) {
        this.persistenceId = persistenceId;

        log.debug("Start new process instance....");

        startWith(State.NEW, new StateData());

        when(State.NEW,
                matchEvent(AddLastTaskCmd.class,    this::handleAddLastTask)
                    .event(GetStateDataCmd.class,   this::handleGetStateData)
                    .event(GetStateCmd.class,       this::handleGetState)
        );

        when(State.PREPARING,
                matchEvent(AddLastTaskCmd.class,    this::handleAddLastTask)
                    .event(SetReadyCmd.class,       this::handleSetToReady)
                    .event(GetStateDataCmd.class,   this::handleGetStateData)
                    .event(GetStateCmd.class,       this::handleGetState)
                );

        when(State.READY,
                matchEvent(GetStateDataCmd.class,   this::handleGetStateData)
                    .event(GetStateCmd.class,       this::handleGetState)
                    .event(GetChildsCmd.class,      this::handleGetChilds)
                    .event(RecoverErrorOccurred.class, (recoverErrorOccurred, stateData) -> goTo(State.RECOVERING_ERROR))
        );

        when(State.RECOVERING_ERROR,
                matchEvent(GetStateDataCmd.class,   this::handleGetStateData)
                        .event(GetStateCmd.class,       this::handleGetState)
        );

    }

    @Override
    public StateData applyEvent(Events domainEvent, StateData currentData) {
        log.debug("Applying event. Recovery: {}. EventData: {}",recoveryRunning() ,domainEvent);

        if (domainEvent instanceof AddLastTaskEvt) {
            val context = ((AddLastTaskEvt) domainEvent).context;

            currentData.taskContextSet.remove(context); // Чтобы всегда оставался последний
            currentData.taskContextSet.add(context);
        } else if (domainEvent instanceof SetToReadyEvt){

            if (recoveryRunning()) {
                try {
                    createChildsForReadyState(currentData);
                } catch (Exception e) {
                    log.error(e, "Error creating task actor");
                    getSelf().forward(new RecoverErrorOccurred(e), getContext());
                }
            }
        }else {
            throw new RuntimeException("Unhandled event");
        }
        return currentData;
    }

    private void createChildsForReadyState(StateData stateData) {
        childTaskActorsCache.clear();
        for (val taskContext : stateData.taskContextSet) {
            if (taskContext.taskProp.equals(Props.empty()))
                throw new RuntimeException("Props is empty!");
            ActorRef taskRef = getContext().actorOf(taskContext.taskProp, taskContext.identifier.toString());
            getContext().watch(taskRef);

            childTaskActorsCache.put(taskRef, taskContext);
        }
    }


    private PersistentFSM.State handleGetStateData(GetStateDataCmd getStateDataCmd, StateData stateData){
        return stay().replying(stateData);
    }

    private PersistentFSM.State handleGetState(GetStateCmd getStateCmd, StateData stateData){
        return stay().replying(stateName());
    }


    private PersistentFSM.State handleAddLastTask(AddLastTaskCmd command, StateData stateData){

        if (command.context.taskProp.equals(Props.empty()))
            return stay().replying(
                    new TaskAddingError(
                            new RuntimeException("Props is empty")
                    ));

        AddLastTaskEvt evt = new AddLastTaskEvt(command.context);
        return goTo(State.PREPARING)
                .applying(evt)
                .replying(new TaskSuccessAdded());
    }


    private PersistentFSM.State handleSetToReady(SetReadyCmd getStateDataCmd, StateData stateData){
        try {
            createChildsForReadyState(stateData);
        }catch (Exception e){
            return stay().replying(new ErrorGoToReady(e));
        }

        return goTo(State.READY)
                .applying(new SetToReadyEvt())
                .replying(new SuccessGoToReady());
    }


    private PersistentFSM.State handleGetChilds(GetChildsCmd getStateDataCmd, StateData stateData){
        ChildTaskList replyValue = new ChildTaskList(
                new ArrayList<>(childTaskActorsCache.keySet())
        );
        return stay()
                .replying(replyValue);
    }


    /**========================================*
     *                 STATE                   *
     *=========================================*/

    public enum State implements PersistentFSM.FSMState {
        NEW("New empty process"),
        RECOVERING_ERROR("Error occurred when recover"),
        PREPARING("Process in preparing task context task"),
        READY("Ready to start");

        private final String identifier;

        State(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String identifier() {
            return identifier;
        }
    }

    public static class StateData {
        public HashSet<TaskExecutionContext> taskContextSet = new HashSet<>();
    }

    /**========================================*
     *                 EVENTS                  *
     *=========================================*/

    interface Events extends Serializable {}

    @Value
    private static class AddLastTaskEvt implements Events{
        TaskExecutionContext context;
    }

    @Value
    private static class SetToReadyEvt implements Events{}

    /**========================================*
     *                 COMMANDS                *
     *=========================================*/

    interface Command {}

    @Value
    public static final class AddLastTaskCmd implements Serializable{
        TaskExecutionContext context;
    }

    @Value
    public static final class SetReadyCmd implements Command {}



    @Value
    public static final class GetStateDataCmd implements Command {}

    @Value
    public static final class GetStateCmd implements Command {}

    @Value
    public static final class GetChildsCmd implements Command {}

    @Value
    public static final class RecoverErrorOccurred implements Command {
        Exception e;
    }

    /**========================================*
     *                 OTHER                   *
     *=========================================*/

    @Value
    @EqualsAndHashCode(of = {"identifier"})
    public static class TaskExecutionContext implements Serializable {
        UUID identifier;
        Props taskProp;
        List<Tuple2<String, String>> innerPropsFromContext;
        List<Tuple2<String, String>> innerPropsFromLastOutput;
        List<Tuple2<String, String>> outerPropsToContext;
    }

    @Value
    public static final class TaskSuccessAdded{}

    @Value
    public static final class TaskAddingError{
        Exception e;
    }

    @Value
    public static final class SuccessGoToReady {}

    @Value
    public static final class ErrorGoToReady {
        Exception e;
    }

    @Value
    public static final class ChildTaskList{
        List<ActorRef> tasks;
    }

}
