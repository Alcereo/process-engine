package ru.alcereo.processdsl.process;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.persistence.fsm.AbstractPersistentFSM;
import akka.persistence.fsm.PersistentFSM;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Value;
import lombok.val;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.Option;
import scala.Tuple2;
import scala.reflect.ClassTag;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import static ru.alcereo.processdsl.task.PersistFSMTask.*;

/**
 * Created by alcereo on 01.01.18.
 */
public class Process extends AbstractPersistentFSM<Process.State, Process.StateData, Process.Events> {

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


    private final String persistenceId;

    public static Props props(String persistenceId){
        return Props.create(Process.class, () -> new Process(persistenceId));
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

        log().debug("Start new process instance....");

        startWith(State.NEW, new StateData());

        when(State.NEW,
                matchEvent(AddLastTaskCmd.class,        this::handleAddLastTask)        //  ADD TASK
                    .event(AppendToContextCmd.class,    this::handleAppendToContext)    // -CONTEXT
                    .event(SetContextCmd.class,         this::handleSetContext)         // -CONTEXT
                    .event(GetContextCmd.class,         this::handleGetContext)         // -CONTEXT
                    .event(GetStateDataCmd.class,       this::handleGetStateData)       // =STATE
                    .event(GetStateCmd.class,           this::handleGetState)           // =STATE
        );

        when(State.PREPARING,
                matchEvent(AddLastTaskCmd.class,        this::handleAddLastTask)
                    .event(AppendToContextCmd.class,    this::handleAppendToContext)
                    .event(SetContextCmd.class,         this::handleSetContext)
                    .event(GetContextCmd.class,         this::handleGetContext)
                    .event(SetReadyCmd.class,           this::handleSetToReady)
                    .event(GetStateDataCmd.class,       this::handleGetStateData)
                    .event(GetStateCmd.class,           this::handleGetState)
                );

        when(State.READY,
                matchEvent(GetStateDataCmd.class,       this::handleGetStateData)
                    .event(GetStateCmd.class,           this::handleGetState)
                    .event(GetChildsCmd.class,          this::handleGetChilds)
                    .event(AppendToContextCmd.class,    this::handleAppendToContext)
                    .event(SetContextCmd.class,         this::handleSetContext)
                    .event(GetContextCmd.class,         this::handleGetContext)
                    .event(GetTasksStatesCmd.class,     this::handleGetTasksState)
                    .event(TaskState.class,             this::handleTaskState)
                    .event(StartProcessCmd.class,       this::handleStartProcess)
//                    .event(TaskEvents.class,            this::handleTasksEvents)
                    .event(RecoverErrorOccurred.class, (recoverErrorOccurred, stateData) -> goTo(State.RECOVERING_ERROR))
        );

        when(State.START,
                matchEvent(GetStateDataCmd.class,       this::handleGetStateData)
                    .event(GetStateCmd.class,           this::handleGetState)
                    .event(GetChildsCmd.class,          this::handleGetChilds)
                    .event(GetTasksStatesCmd.class,     this::handleGetTasksState)
                    .event(TaskState.class,             this::handleTaskState)
                    .event(TaskEvents.class,            this::handleTasksEvents)
        );

        when(State.RECOVERING_ERROR,
                matchEvent(GetStateDataCmd.class,       this::handleGetStateData)
                        .event(GetStateCmd.class,       this::handleGetState)
        );

    }

    @Override
    public StateData applyEvent(Events domainEvent, StateData currentData) {
        log().debug("Applying event. Recovery: {}. EventData: {}",recoveryRunning() ,domainEvent);

        if (domainEvent instanceof AddLastTaskEvt) {
            currentData.addLastTask(((AddLastTaskEvt) domainEvent).context);
        } else if (domainEvent instanceof SetToReadyEvt){

            if (recoveryRunning()) {
                try {
                    createChildsForReadyState(currentData);
                } catch (Exception e) {
                    log().error(e, "Error creating task actor");
                    getSelf().forward(new RecoverErrorOccurred(e), getContext());
                }
            }
        } else if (domainEvent instanceof SetContextEvt){
            currentData.processContext = ((SetContextEvt) domainEvent).context;
        }else {
            throw new RuntimeException("Unhandled event");
        }
        return currentData;
    }

    private void createChildsForReadyState(StateData stateData) {
        stateData.childTaskActorsCache.clear();
        for (val taskContext : stateData.getTasksContexts()) {
            if (taskContext.taskProp.equals(Props.empty()))
                throw new RuntimeException("Props is empty!");
            ActorRef taskRef = getContext().actorOf(taskContext.taskProp, taskContext.identifier.toString());
            getContext().watch(taskRef);

            stateData.childTaskActorsCache.put(taskRef, taskContext);

            //Отправим запрос, чтобы узнать статус таски. Актор персистентный,
            // задача могла быть создана уже в каком-нибудь не дефолтном статусе
            taskRef.tell(new GetTaskStateCmd(), getSelf());
        }
    }

    /**========================================*
     *                 STATE                   *
     *=========================================*/

    public enum State implements PersistentFSM.FSMState {
        NEW("New empty process"),
        RECOVERING_ERROR("Error occurred when recover"),
        PREPARING("Process in preparing task context task"),
        READY("Ready to start"),
        START("Process is executing tasks");

        private final String identifier;

        State(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String identifier() {
            return identifier;
        }
    }

    @Data
    public static class StateData {
        private ArrayList<TaskExecutionContext> taskContextList = new ArrayList<>();
        private final Map<UUID, PersistFSMTask.TaskState> tasksStatuses = new HashMap<>();
        private final Map<ActorRef, TaskExecutionContext> childTaskActorsCache = new HashMap<>();
        private Map<String, Object> processContext = new HashMap<>();

//        util func

        void addLastTask(TaskExecutionContext taskContext){
            taskContextList.add(taskContext);
        }

        public Option<UUID> getIdentifierByActorRef(ActorRef ref){
            return Option.apply(childTaskActorsCache.get(ref))
                    .map(taskContext -> taskContext.identifier);
        }

        public Option<ActorRef> getActorRefByIdentifier(UUID identifier) {
            return Option.apply(childTaskActorsCache
                            .entrySet().stream()
                            .filter(entry -> entry.getValue().identifier.equals(identifier))
                            .map(Map.Entry::getKey)
                            .findFirst().orElse(null));
        }

        public void setTaskState(UUID identifier, TaskState taskState) {
            tasksStatuses.put(identifier, taskState);
        }

        public List<TaskExecutionContext> getTasksContexts() {
            return taskContextList;
        }

        public boolean containsIdentifier(UUID identifier) {
            return taskContextList.stream().anyMatch(context -> context.identifier.equals(identifier));
        }

        public Option<PersistFSMTask.TaskState> taskState(UUID identifier){
            return Option.apply(tasksStatuses.get(identifier));
        }

        public List<ActorRef> getTaskRefs() {
            return taskContextList.stream()
                    .map(TaskExecutionContext::getIdentifier)
                    .map(this::getActorRefByIdentifier)
                    .map(actorRefOption -> actorRefOption.fold(() -> null, v1 -> v1))
                    .collect(Collectors.toList());
        }
    }


    /**========================================*
     *                 HANDLERS                *
     *=========================================*/

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

        if (stateData.containsIdentifier(command.context.identifier))
            return stay().replying(
                    new TaskAddingError(
                            new RuntimeException("Identifier is not unique")
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
                stateData.getTaskRefs()
        );
        return stay()
                .replying(replyValue);
    }

    private PersistentFSM.State handleAppendToContext(AppendToContextCmd command, StateData stateData){

        val context = stateData.processContext;
        context.putAll(command.properties);

        return stay()
                .applying(new SetContextEvt(context))
                .replying(new SuccessSetContext());
    }

    private PersistentFSM.State handleSetContext(SetContextCmd command, StateData stateData){
        return stay()
                .applying(new SetContextEvt(command.properties))
                .replying(new SuccessSetContext());
    }


    private PersistentFSM.State handleGetContext(GetContextCmd command, StateData stateData){
        return stay()
                .replying(new ProcessContextMessage(stateData.processContext));
    }

    private PersistentFSM.State handleTaskState(TaskState taskState, StateData stateData){

        return stateData.getIdentifierByActorRef(getSender())
                .fold(() -> {
                    log().warning("Get message from not registered child: {}", getSender());
                    return stay();
                },
                identifier -> {
                    log().debug("Get task state message: {} - {}", getSender(), taskState);
                    stateData.setTaskState(identifier, taskState);
                    return stay();
                }
        );
    }

    private PersistentFSM.State handleGetTasksState(GetTasksStatesCmd command, StateData stateData){
        return stay()
                .replying(new TasksStatesMessage(stateData.tasksStatuses));
    }

    private PersistentFSM.State handleTasksEvents(TaskEvents event, StateData stateData){
        log().debug("Get event from task. Event: {}, Task: {}", event, getSender());

        getSender().tell(new GetTaskStateCmd(), getSelf());

        if (event instanceof PreparedEvt){
            getSender().tell(new ExecuteCmd(), getSelf());
            return stay();
        }else {
            log().error("Unregistered event from task: {}. Actor: {}", event, getSender());
            return stay();
        }
    }

    private PersistentFSM.State handleStartProcess(StartProcessCmd command, StateData stateData){

        TaskExecutionContext firstTaskContext = stateData.getTasksContexts().get(0);

        val taskStateOpt = stateData.taskState(firstTaskContext.identifier);

        if (taskStateOpt.isEmpty())
            return stay().replying(new ProcessStartError(new RuntimeException("Don`t found first task id in statuses list")));

        if (!taskStateOpt.get().equals(TaskState.NEW))
            return stay().replying(new ProcessStartError(new RuntimeException("First task not in NEW status")));


        val taskRef = stateData.getActorRefByIdentifier(firstTaskContext.identifier);

        if (taskRef.isEmpty())
            return stay().replying(new ProcessStartError(new RuntimeException("Don`t found task ref by identifier")));

        val propertyIn = new HashMap<String, Object>();

        firstTaskContext.innerPropsFromContext
                .forEach(strTuple -> propertyIn.put(
                                            strTuple._2(),
                                            stateData.getProcessContext().get(strTuple._1())
                        )
                );


        taskRef.get().tell(new PrepareCmd(propertyIn), getSelf());

        return goTo(State.START).replying(new SuccessStartProcess());
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

    @Value
    private static class SetContextEvt implements Events {
        Map<String, Object> context;
    }

//    @Value
//    private class TaskStateChangeEvt implements Events {
//        ActorRef sender;
//        PersistFSMTask.TaskState taskState;
//    }
//
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

    @Value
    public static final class AppendToContextCmd {
        Map<String, Object> properties;
    }

    @Value
    public static final class SetContextCmd {
        Map<String, Object> properties;
    }

    @Value
    public static final class GetContextCmd {}

    @Value
    public static final class GetTasksStatesCmd {}

    @Value
    public static final class StartProcessCmd {}

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

    @Value
    public static final class SuccessSetContext {}

    @Value
    public static final class ProcessContextMessage {
        Map<String, Object> processContext;
    }

    @Value
    public static final class TasksStatesMessage {
        Map<UUID, PersistFSMTask.TaskState> tasksStatuses;
    }

    @Value
    public static class SuccessStartProcess {}

    @Value
    public static class ProcessStartError {
        RuntimeException e;
    }
}
