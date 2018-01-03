package ru.alcereo.processdsl.taks;

import akka.actor.AbstractActor;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by alcereo on 01.01.18.
 */
public class Task extends AbstractActor {

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);

    public static Props props(){
        return Props.create(Task.class, Task::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(TaskExecuteMessage.class, this::handleTaskExecute)
                .build();
    }

    private void handleTaskExecute(TaskExecuteMessage message) {

        log.debug("Perfofrm message: {}", message);

        System.out.println(message.properties.get("text"));

        Map<String, Object> outProps = new HashMap<>();
        outProps.put("outcome", "Some context part");
        outProps.put("text", message.properties.get("text") +"| add some ");

        getSender().tell(new SuccessExecuting(outProps), getSelf());
    }

    @Data
    public static class TaskExecuteMessage {
        public final Map<String, Object> properties;
    }

    @Data
    public static class SuccessExecuting {
        public final Map<String, Object> properties;
    }
}
