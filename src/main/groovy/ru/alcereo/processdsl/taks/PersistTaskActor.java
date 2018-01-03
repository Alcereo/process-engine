package ru.alcereo.processdsl.taks;

import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.persistence.AbstractPersistentActor;
import lombok.Data;
import lombok.Value;

import java.io.Serializable;
import java.util.Map;

/**
 * Created by alcereo on 03.01.18.
 */
public class PersistTaskActor extends AbstractPersistentActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    private final String persistentId;

    private final TaskState taskState;

    public PersistTaskActor(String persistedId) {
        this.persistentId = persistedId;
        this.taskState = new TaskState();
    }

    public static Props props(String persistentId) {
        return Props.create(PersistTaskActor.class, () -> new PersistTaskActor(persistentId));
    }

    @Override
    public String persistenceId() {
        return this.persistentId;
    }

    @Override
    public Receive createReceiveRecover() {
        return receiveBuilder()
                .match(PreparedEvt.class, evt -> {
                    log.debug("Recover prepared event with text: {}", evt.textToPrint);
                    this.taskState.update(evt);

                }).match(SuccessExecutedEvt.class, evt -> {
                    log.debug("Recover success executed event");
                    this.taskState.update(evt);

                })
                .build();
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(PrepareCmd.class, prepareCmd -> {
                    log.debug("Get prepareCmd command with properties: {}", prepareCmd);

                    PreparedEvt preparedEvt = new PreparedEvt(
                            prepareCmd.getProperties().get("text").toString()
                    );

                    persist(preparedEvt, (PreparedEvt preparedEvtHandled) -> {
                        log.debug("Handle preparedEvent with text: {}", preparedEvtHandled.textToPrint);
                        this.taskState.update(preparedEvtHandled);
                        getSender().tell(preparedEvtHandled, getSelf());
                    });

                }).match(ExecuteCmd.class, executeCmd -> {
                    log.debug("Get execute command");

                    System.out.println(" =========== " + this.taskState.textToPrint + " =========== ");

                    persist(new SuccessExecutedEvt(), (SuccessExecutedEvt evt) -> {
                        log.debug("Success executed evt handled");
                        this.taskState.update(evt);
                    });

                }).match(GetStateMsg.class, getStateMsg -> {
                    log.debug("Get state getting msg");

                    getSender().tell(this.taskState, getSelf());

                })
                .build();
    }

    @Data
    public static class TaskState {
        private Stage stage = Stage.NEW;
        private String textToPrint;

        public void update(PreparedEvt param) {
            this.textToPrint = param.textToPrint;
            this.stage = Stage.PREPARED;
        }

        public void update(SuccessExecutedEvt evt) {
            this.stage = Stage.RUNED;
        }

        public enum Stage{
            NEW,
            PREPARED,
            RUNED
        }
    }

//    Prepare

    @Value
    public static class PreparedEvt implements Serializable{
        static final long serialVersionUID = 1L;
        String textToPrint;
    }

    @Value
    public static class PrepareCmd{
        Map<String, Object> properties;
    }

//    Execute

    @Value
    public static class ExecuteCmd {
    }

    @Value
    public static class SuccessExecutedEvt implements Serializable{
        static final long serialVersionUID = 1L;
    }

//    Get state
    @Value
    public static class GetStateMsg {
    }
}
