package ru.alcereo.processdsl.process;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.persistence.fsm.AbstractPersistentFSM;
import akka.persistence.fsm.PersistentFSM;
import lombok.Value;
import lombok.val;
import ru.alcereo.processdsl.domain.Process;
import ru.alcereo.processdsl.domain.Task;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.reflect.ClassTag;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static ru.alcereo.processdsl.task.PersistFSMTask.*;

/**
 * Created by alcereo on 01.01.18.
 */
public class ProcessActor extends AbstractPersistentFSM<ProcessActor.State, Process, ProcessActor.Events> {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String persistenceId;

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

    public static Props props(String persistenceId){
        return Props.create(ProcessActor.class, () -> new ProcessActor(persistenceId));
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
    ProcessActor(String persistenceId, boolean createChild, String childName){
        this(persistenceId);

        if (createChild)
            getContext().actorOf(Props.empty(), childName);
    }

    private ProcessActor(String persistenceId) {
        this.persistenceId = persistenceId;

        log.debug("Start new process instance....");

        startWith(State.NEW, new Process());

        when(State.NEW,
                matchEvent(AddLastTaskCmd.class,            this::handleAddLastTask)        // -ADD TASK
                        .event(AppendToContextCmd.class,    this::handleAppendToContext)    // -CONTEXT
                        .event(SetContextCmd.class,         this::handleSetContext)         // -CONTEXT
                        .event(GetContextCmd.class,         this::handleGetContext)         // -CONTEXT
                        .event(GetStateDataCmd.class,       this::handleGetStateData)       // =STATE
                        .event(GetStateCmd.class,           this::handleGetState)           // =STATE
        );

        when(State.PREPARING,
                matchEvent(AddLastTaskCmd.class,            this::handleAddLastTask)
                        .event(AppendToContextCmd.class,    this::handleAppendToContext)
                        .event(SetContextCmd.class,         this::handleSetContext)
                        .event(GetContextCmd.class,         this::handleGetContext)
                        .event(SetReadyCmd.class,           this::handleSetToReady)
                        .event(GetStateDataCmd.class,       this::handleGetStateData)
                        .event(GetStateCmd.class,           this::handleGetState)
        );

        when(State.READY,
                matchEvent(GetStateDataCmd.class,           this::handleGetStateData)
                        .event(GetStateCmd.class,           this::handleGetState)
                        .event(GetChildsCmd.class,          this::handleGetChilds)
                        .event(AppendToContextCmd.class,    this::handleAppendToContext)
                        .event(SetContextCmd.class,         this::handleSetContext)
                        .event(GetContextCmd.class,         this::handleGetContext)
                        .event(GetTasksStatesCmd.class,     this::handleGetTasksState)
                        .event(TaskState.class,             this::handleTaskState)
                        .event(StartProcessCmd.class,       this::handleStartProcess)
//                    .event(TaskEvents.class,            this::handleTasksEvents)
                        .event(RecoverErrorOccurred.class, (recoverErrorOccurred, process) -> goTo(State.RECOVERING_ERROR))
        );

        when(State.START,
                matchEvent(GetStateDataCmd.class,           this::handleGetStateData)
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
    public Process applyEvent(Events domainEvent, Process process) {
        log.debug("Applying event. Recovery: {}. EventData: {}",recoveryRunning() ,domainEvent);

        if (domainEvent instanceof AddLastTaskEvt) {
            process.addLastTask(((AddLastTaskEvt) domainEvent).task);
        } else if (domainEvent instanceof SetToReadyEvt){

            if (recoveryRunning()) {
                try {
                    createChildsForReadyState(process);
                } catch (Exception e) {
                    log.error(e, "Error creating task actor");
                    getSelf().forward(new RecoverErrorOccurred(e), getContext());
                }
            }
        } else if (domainEvent instanceof SetContextEvt){
            process.setProcessContext(((SetContextEvt) domainEvent).context);
        }else {
            throw new RuntimeException("Unhandled event");
        }
        return process;
    }

    private void createChildsForReadyState(Process process) {
        process.getChildTaskActorsCache().clear();
        for (val taskContext : process.getTasksContexts()) {
            if (taskContext.getTaskProp().equals(Props.empty()))
                throw new RuntimeException("Props is empty!");
            ActorRef taskRef = getContext().actorOf(taskContext.getTaskProp(), taskContext.getIdentifier().toString());
            getContext().watch(taskRef);

            process.getChildTaskActorsCache().put(taskRef, taskContext);

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
        PREPARING("ProcessActor in preparing task task task"),
        READY("Ready to start"),
        START("ProcessActor is executing tasks");

        private final String identifier;

        State(String identifier) {
            this.identifier = identifier;
        }

        @Override
        public String identifier() {
            return identifier;
        }
    }


    /**========================================*
     *                 HANDLERS                *
     *=========================================*/

    private PersistentFSM.State handleGetStateData(GetStateDataCmd getStateDataCmd, Process process){
        return stay().replying(process);
    }

    private PersistentFSM.State handleGetState(GetStateCmd getStateCmd, Process process){
        return stay().replying(stateName());
    }

    private PersistentFSM.State handleAddLastTask(AddLastTaskCmd command, Process process){

        if (command.context.getTaskProp().equals(Props.empty()))
            return stay().replying(
                    new TaskAddingError(
                            new RuntimeException("Props is empty")
                    ));

        if (process.containsIdentifier(command.context.getIdentifier()))
            return stay().replying(
                    new TaskAddingError(
                            new RuntimeException("Identifier is not unique")
                    ));

        AddLastTaskEvt evt = new AddLastTaskEvt(command.context);
        return goTo(State.PREPARING)
                .applying(evt)
                .replying(new TaskSuccessAdded());
    }

    private PersistentFSM.State handleSetToReady(SetReadyCmd getStateDataCmd, Process process){
        try {
            createChildsForReadyState(process);
        }catch (Exception e){
            return stay().replying(new ErrorGoToReady(e));
        }

        return goTo(State.READY)
                .applying(new SetToReadyEvt())
                .replying(new SuccessGoToReady());
    }

    private PersistentFSM.State handleGetChilds(GetChildsCmd getStateDataCmd, Process process){
        ChildTaskList replyValue = new ChildTaskList(
                process.getTaskRefs()
        );

        return stay()
                .replying(replyValue);
    }

    private PersistentFSM.State handleAppendToContext(AppendToContextCmd command, Process process){

        val context = process.getProcessContext();
        context.putAll(command.properties);

        return stay()
                .applying(new SetContextEvt(context))
                .replying(new SuccessSetContext());
    }

    private PersistentFSM.State handleSetContext(SetContextCmd command, Process process){
        return stay()
                .applying(new SetContextEvt(command.properties))
                .replying(new SuccessSetContext());
    }

    private PersistentFSM.State handleGetContext(GetContextCmd command, Process process){
        return stay()
                .replying(new ProcessContextMessage(process.getProcessContext()));
    }

    private PersistentFSM.State handleTaskState(TaskState taskState, Process process){

        return process.getIdentifierByActorRef(getSender())
                .fold(() -> {
                            log.warning("Get message from not registered child: {}", getSender());
                            return stay();
                        },
                        identifier -> {
                            log.debug("Get task state message: {} - {}", getSender(), taskState);
                            process.setTaskState(identifier, taskState);
                            return stay();
                        }
                );
    }

    private PersistentFSM.State handleGetTasksState(GetTasksStatesCmd command, Process process){
        return stay()
                .replying(new TasksStatesMessage(process.getTasksStatuses()));
    }

    private PersistentFSM.State handleTasksEvents(TaskEvents event, Process process){
        log.debug("Get event from task. Event: {}, Task: {}", event, getSender());

        getSender().tell(new GetTaskStateCmd(), getSelf());

        if (event instanceof PreparedEvt){
            getSender().tell(new ExecuteCmd(), getSelf());
            return stay();
        }else {
            log.error("Unregistered event from task: {}. Actor: {}", event, getSender());
            return stay();
        }
    }

    private PersistentFSM.State handleStartProcess(StartProcessCmd command, Process process){

        Task firstTaskContext = process.getTasksContexts().get(0);

        val taskStateOpt = process.taskState(firstTaskContext.getIdentifier());

        if (taskStateOpt.isEmpty())
            return stay().replying(new ProcessStartError(new RuntimeException("Don`t found first task id in statuses list")));

        if (!taskStateOpt.get().equals(TaskState.NEW))
            return stay().replying(new ProcessStartError(new RuntimeException("First task not in NEW status")));


        val taskRef = process.getActorRefByIdentifier(firstTaskContext.getIdentifier());

        if (taskRef.isEmpty())
            return stay().replying(new ProcessStartError(new RuntimeException("Don`t found task ref by identifier")));

        val propertyIn = new HashMap<String, Object>();

        firstTaskContext.getInnerPropsFromContext()
                .forEach(strTuple -> propertyIn.put(
                        strTuple._2(),
                        process.getProcessContext().get(strTuple._1())
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
        Task task;
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
        Task context;
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
