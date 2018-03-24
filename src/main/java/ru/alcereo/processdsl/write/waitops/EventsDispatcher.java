package ru.alcereo.processdsl.write.waitops;

import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.LoggingReceive;
import akka.persistence.AbstractPersistentActor;
import lombok.NonNull;

public class EventsDispatcher extends AbstractPersistentActor{

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);


    private final String persistentId;

    public EventsDispatcher(String persistentId) {
        this.persistentId = persistentId;
    }

    public static Props props(@NonNull String persistentId){
        return Props.create(EventsDispatcher.class,
                () -> new EventsDispatcher(persistentId)
        );
    }

    @Override
    public String persistenceId() {
        return persistentId;
    }

    @Override
    public Receive createReceiveRecover() {
        return LoggingReceive.create(
                receiveBuilder()
                        .build(),
                getContext()
        );
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(SubscribeRequestMessage.class, this::handleSubscribeRequestMessage)
                        .build(),
                getContext()
        );
    }

    private void handleSubscribeRequestMessage(SubscribeRequestMessage msg) {

    }


    public interface SubscribeRequestMessage {
    }
}
