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
import ru.alcereo.processdsl.domain.BusinessProcess;
import ru.alcereo.processdsl.domain.Task;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.concurrent.Future;

import java.io.Serializable;
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
                .match(CreateNewProcessCmd.class,               this::handleCommand)
                .match(AddLastTaskCmd.class,                    this::handleCommand)
                .match(StartProcessCmd.class,                   this::handleCommand)
                .match(PersistFSMTask.SuccessExecutedEvt.class, this::handleEvent)
                .matchAny(o -> log().error("Unhandled message: {}", o))
                .build();
    }


    /**========================================*
     *                HANDLERS                 *
     *=========================================*/

    private void handleCommand(AddLastTaskCmd cmd) {

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

                        process.addLastTask(cmd.getTask());

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
                }),
                getContext().dispatcher());

        log.debug("Handling add task finished");

    }

    private void handleCommand(CreateNewProcessCmd cmd) {

        final ActorRef initSender = getSender();

        val process = new BusinessProcess(cmd.getUuid());

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
                }),
                getContext().dispatcher());

        log.debug("Start process handling finished");

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

                        Task task = process.getFirstTask();
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
                }),
                getContext().dispatcher());

    }

    private void handleEvent(PersistFSMTask.SuccessExecutedEvt evt) {

        log().debug("Handle task success execution event: {}", evt);

//        final ActorRef initSender = getSender();

        Future<Object> processF = Patterns
                .ask(processRepository,
                        new ProcessRepositoryAbstractActor.GetProcessByTaskUid(evt.getTaskUid()),
                        Timeout.apply(5, TimeUnit.SECONDS)
                );

        Future<Object> resultF = processF.flatMap(
                result -> {
                    log().debug("Get process from repo: {}", result);
                    if (result instanceof BusinessProcess) {

                        final BusinessProcess process = (BusinessProcess) result;

                        if (process.isLastTask(evt.getTaskUid())){
                            return Futures.successful("Process finished");
                        }else {
                            val taskOpt = process.getNextTaskAfter(evt.getTaskUid());
                            if (taskOpt.isPresent()) {
                                val task = taskOpt.get();
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
                            } else {
                                return Futures.failed(new Exception("Next task not found: " + evt.getTaskUid()));
                            }
                        }
                    }else if (result instanceof ProcessRepositoryAbstractActor.ProcessNotFound) {
                        return Futures.failed(new Exception("Process not found with task uid: "+ evt.getTaskUid()));
                    } else {
                        return Futures.failed(new Exception("Expect class BusinessProcess. get: " + result.getClass().getName()));
                    }
                }, getContext().dispatcher());

        resultF.onFailure(
                failure(throwable -> {
                    log().error(throwable, "Error updating process");
//                    initSender.tell(new CommandException(throwable), getSelf());
                }),
                getContext().dispatcher());
//
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
    public static class AddLastTaskCmd implements Command {
        public UUID processUid;
        public Task task;
    }

    @Value
    public static class StartProcessCmd implements Command {
        public UUID processUid;
    }

    @Value
    public static class CreateNewProcessCmd implements Command {
        UUID uuid;
    }

    @Value
    public static class CommandException implements Command {
        Throwable exception;
    }

    @Value
    public static class SuccessCommand implements Command {
    }

}
