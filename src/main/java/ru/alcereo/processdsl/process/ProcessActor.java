package ru.alcereo.processdsl.process;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.dispatch.Futures;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.Value;
import lombok.val;
import ru.alcereo.processdsl.domain.AcceptResultOnFinishException;
import ru.alcereo.processdsl.domain.BusinessProcess;
import ru.alcereo.processdsl.domain.task.AbstractTask;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.concurrent.Future;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;
import static ru.alcereo.processdsl.Utils.success;

/**
 * Created by alcereo on 01.01.18.
 */
public class ProcessActor extends AbstractLoggingActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private ActorRef processRepository;
    private List<ActorRef> observers = new ArrayList<>();

    //  ProcessInMemoryRepository by props

    public static Props props(Props processRepositoryProp){
        return Props.create(ProcessActor.class, () -> new ProcessActor(processRepositoryProp));
    }

    private ProcessActor(Props processRepositoryProp) {
        this.processRepository = getContext().actorOf(processRepositoryProp, "process-repository");
    }

    //  ProcessInMemoryRepository by actorRef

    public static Props props(ActorRef processRepository){
        return Props.create(ProcessActor.class, () -> new ProcessActor(processRepository));
    }

    private ProcessActor(ActorRef processRepository) {
        this.processRepository = processRepository;
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
//                Commands
                .match(CreateNewProcessCmd.class,               this::handleCommand)
                .match(SetTasksToProcessCmd.class,              this::handleCommand)
                .match(StartProcessCmd.class,                   this::handleCommand)
//                Events
                .match(PersistFSMTask.SuccessExecutedEvt.class,     this::handleEvent)
                .match(PersistFSMTask.ExecutedWithErrorsEvt.class,  this::handleEvent)
//                Observers
                .match(AddObserverMsg.class,                    this::addObserver)
                .match(DeleteObserver.class,                    this::deleteObserver)

                .matchAny(o -> log().error("Unhandled message: {}", o))
                .build();
    }

    /**========================================*
     *            SERVICE HANDLERS             *
     *=========================================*/

    private void addObserver(AddObserverMsg msg) {
        this.observers.add(msg.getObserver());
        getSender().tell(new SuccessAdded(), getSelf());
    }

    private void deleteObserver(DeleteObserver msg) {
        if (this.observers.remove(msg.getObserver()))
            getSender().tell(new SuccessDelete(), getSelf());
        else
            getSender().tell(new NotFoundOnDelete(), getSelf());
    }

    /**========================================*
     *                HANDLERS                 *
     *=========================================*/

    private void handleCommand(CreateNewProcessCmd cmd) {

        final ActorRef initSender = getSender();

        val process = BusinessProcess.builder()
                .identifier(cmd.getUuid())
                .processContext(cmd.getProperties())
                .build();

        Future<Object> processF = Patterns
                .ask(processRepository,
                        new ProcessRepositoryAbstractActor.AddProcess(process),
                        Timeout.apply(5, TimeUnit.SECONDS)
                );

        processF.onFailure(
                failure(throwable -> {
                    log().error(throwable, "Error creating process");
                    initSender.tell(new CommandException(throwable), getSelf());
                }),
                getContext().dispatcher());

        processF.onSuccess(
                success((Object result) ->{
                    log().debug("Success creating process: {}", result);
                    initSender.tell(new SuccessCommand(), getSelf());
//                    TODO: Конечно нужно что-то на подобии Event-bus
                    observers.forEach(actorRef -> actorRef.tell(
                            new BusinessProcess.ProcessCreatedEvt(cmd.getUuid()),
                            getSelf()
                    ));
                }),
                getContext().dispatcher());

        log.debug("Start process handling finished");

    }

    private void handleCommand(SetTasksToProcessCmd cmd) {

        final ActorRef initSender = getSender();

        Future<Object> processF = Patterns
                .ask(processRepository,
                        new ProcessRepositoryAbstractActor.GetProcessByUID(cmd.getProcessUid()),
                        Timeout.apply(5, TimeUnit.SECONDS)
                );

        Future<Object> addTaskAndUpdateF = processF.flatMap(
                result -> {
                    log().debug("Get process from repo: {}", result);
                    if (result instanceof BusinessProcess) {

                        final BusinessProcess process = (BusinessProcess) result;

                        process.setHeaderTask(cmd.getTask());

                        return Patterns.ask(processRepository,
                                new ProcessRepositoryAbstractActor.UpdateProcess(process),
                                Timeout.apply(5, TimeUnit.SECONDS)
                        );
                    }else if (result instanceof ProcessRepositoryAbstractActor.ProcessNotFound) {
                        return Futures.failed(new Exception("Process not found with uid: "+ cmd.getProcessUid()));
                    } else {
                        return Futures.failed(new Exception("Expect class BusinessProcess. get: " + result.getClass().getName()));
                    }
                }, getContext().dispatcher());


        addTaskAndUpdateF.onFailure(
                failure(throwable -> {
                    log().error(throwable, "Error updating process");
                    initSender.tell(new CommandException(throwable), getSelf());
                }),
                getContext().dispatcher());

        addTaskAndUpdateF.onSuccess(
                success((Object result) ->{
                    log().debug("Success updatig process: {}", result);
                    initSender.tell(new SuccessCommand(), getSelf());
                    observers.forEach(actorRef -> actorRef.tell(
                            new BusinessProcess.LastTaskAddedEvt(cmd.getTask()),
                            getSelf()
                    ));
                }),
                getContext().dispatcher());

        log.debug("Handling add task finished");

    }

    private void handleCommand(StartProcessCmd cmd) {

        log().debug("Start process with id: {}", cmd.getProcessUid());

        final ActorRef initSender = getSender();

        Future<Object> processF = Patterns
                .ask(processRepository,
                        new ProcessRepositoryAbstractActor.GetProcessByUID(cmd.getProcessUid()),
                        Timeout.apply(5, TimeUnit.SECONDS)
                );

        Future<Object> resultF = processF.flatMap(
                result -> {
                    log().debug("Get process from repo: {}", result);
                    if (result instanceof BusinessProcess) {

//                        Main method place
                        final BusinessProcess process = (BusinessProcess) result;

                        AbstractTask task = process.getCurrentTask();
                        ActorRef taskActor = getContext().actorOf(task.getType().getTaskActorProps(), "task-" + task.getIdentifier());

                        log().debug("Send command to append properties to actor task");

                        return Patterns.ask(
                                taskActor,
                                new PersistFSMTask.AppendToContextCmd(task.getProperties()),
                                Timeout.apply(5, TimeUnit.SECONDS)
                        ).flatMap(
                                appendResult -> {
                                    log().debug("Get append command result: {}", result);

                                    if (appendResult instanceof PersistFSMTask.CmdSuccess){

                                        log().debug("Send start task command");
                                        return Patterns.ask(
                                                taskActor,
                                                new PersistFSMTask.StartExecutingCmd(),
                                                Timeout.apply(5, TimeUnit.SECONDS)
                                        );
                                    }else {
                                        return Futures.failed(new Exception("Append properties error: "+ appendResult));
                                    }
                                }, getContext().dispatcher()
                        );

                    }else if (result instanceof ProcessRepositoryAbstractActor.ProcessNotFound) {
                        return Futures.failed(new Exception("Process not found with uid: "+ cmd.getProcessUid()));
                    } else {
                        return Futures.failed(new Exception("Expect class BusinessProcess. get: " + result.getClass().getName()));
                    }
                }, getContext().dispatcher());


        resultF.onFailure(
                failure(throwable -> {
                    log().error(throwable, "Error starting process");
                    initSender.tell(new CommandException(throwable), getSelf());
                }),
                getContext().dispatcher());

        resultF.onSuccess(
                success((Object result) ->{
                    log().debug("Success starting process: {}", cmd);
                    initSender.tell(new SuccessCommand(), getSelf());
                    observers.forEach(
                            actorRef ->
                                    actorRef.tell(
                                            new BusinessProcess.ProcessStartedEvt(cmd.getProcessUid()),
                                            getSelf()
                                    )
                    );
                }),
                getContext().dispatcher());

    }

    private void handleEvent(PersistFSMTask.SuccessExecutedEvt evt) {
        log().debug("Handle task success execution event: {}", evt);

        handleTaskResult(
                evt.getTaskUid(),
                new AbstractTask.SuccessTaskResult(evt.getTaskUid(), evt.getProperties())
        );
    }

    private void handleEvent(PersistFSMTask.ExecutedWithErrorsEvt evt){
        log().debug("Handle task error execution event: {}", evt);

        handleTaskResult(
                evt.getTaskUid(),
                new AbstractTask.FailureTaskResult(evt.getTaskUid(), evt.getError(), evt.getProperties())
        );
    }

    private void handleTaskResult(UUID taskUuid, AbstractTask.TaskResult taskResult){

        Future<Object> processF = Patterns
                .ask(processRepository,
                        new ProcessRepositoryAbstractActor.GetProcessByTaskUid(taskUuid),
                        Timeout.apply(5, TimeUnit.SECONDS)
                );

        Future<Object> resultF = processF.flatMap(
                result -> {
                    log().debug("Get process from repo: {}", result);
                    if (result instanceof BusinessProcess) {

                        final BusinessProcess process = (BusinessProcess) result;

                        try {
                            process.acceptCurrentTaskResult(taskResult);
                        } catch (AcceptResultOnFinishException e) {
                            return Futures.failed(new Exception("Process already finished: " + taskUuid, e));
                        }

                        if (process.isFinished()){
                            observers.forEach(
                                    actorRef ->
                                            actorRef.tell(
                                                    new BusinessProcess.ProcessFinishedEvt(process.getIdentifier()),
                                                    getSelf()
                                            )
                            );
                            return Futures.successful("Process finished");
                        }else {
                            val task = process.getCurrentTask();
                            val taskActor = getContext().actorOf(task.getType().getTaskActorProps(), "task-" + task.getIdentifier());

                            log().debug("Send command to append properties to actor task");

                            return Patterns.ask(
                                    taskActor,
                                    new PersistFSMTask.AppendToContextCmd(task.getProperties()),
                                    Timeout.apply(5, TimeUnit.SECONDS)
                            ).flatMap(
                                    appendResult -> {
                                        log().debug("Get append command result: {}", result);

                                        if (appendResult instanceof PersistFSMTask.CmdSuccess) {

                                            log().debug("Send start task command");
                                            return Patterns.ask(
                                                    taskActor,
                                                    new PersistFSMTask.StartExecutingCmd(),
                                                    Timeout.apply(5, TimeUnit.SECONDS)
                                            );
                                        } else {
                                            return Futures.failed(new Exception("Append properties error: " + appendResult));
                                        }
                                    }, getContext().dispatcher()
                            );
                        }
                    }else if (result instanceof ProcessRepositoryAbstractActor.ProcessNotFound) {
                        return Futures.failed(new Exception("Process not found with task uid: "+ taskUuid));
                    } else {
                        return Futures.failed(new Exception("Expect class BusinessProcess. get: " + result.getClass().getName()));
                    }
                }, getContext().dispatcher());

        resultF.onFailure(
                failure(throwable -> {
                    log().error(throwable, "Error updating process");
                }),
                getContext().dispatcher());

//        resultF.onSuccess(
//                success((Object result) ->{
//                    log().debug("Success updatig process: {}", result);
//                    initSender.tell(new SuccessCommand(), getSelf());
//                }),
//                getContext().dispatcher());
    }

    /**========================================*
     *                COMMANDS                 *
     *=========================================*/

    interface Command extends Serializable {}

    @Value
    public static class CreateNewProcessCmd implements Command {
        UUID uuid;
        Map<String, Object> properties;
    }

    @Value
    public static class SetTasksToProcessCmd implements Command {
        public UUID processUid;
        public AbstractTask task;
    }

    @Value
    public static class StartProcessCmd implements Command {
        public UUID processUid;
    }


    @Value
    public static class CommandException implements Command {
        Throwable exception;
    }

    @Value
    public static class SuccessCommand implements Command {
    }

    /**========================================*
     *                MESSAGES                 *
     *=========================================*/

    @Value
    public static class AddObserverMsg {
        ActorRef observer;
    }

    @Value
    public static class DeleteObserver {
        ActorRef observer;
    }

    @Value
    public static class SuccessAdded {
    }

    @Value
    public static class SuccessDelete {
    }

    @Value
    public static class NotFoundOnDelete {
    }

}
