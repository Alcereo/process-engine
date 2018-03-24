package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.event.LoggingReceive;
import lombok.Getter;

import java.util.Objects;


/**
 * - Ловить только основные паттерны и заворачивать в читаемый объект.
 * Более делатьные совпдаения будет искать клиент - таска
 *
 *
 */
public abstract class AbstractMessageEventObserver<T> extends AbstractLoggingActor{

    private final ActorRef dispatcherRouter;

    public AbstractMessageEventObserver(ActorRef dispatcherRouter) {
        this.dispatcherRouter = Objects.requireNonNull(dispatcherRouter);
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(MessageDeserializer.StateChangeMessage.class, this::handleJsonObjectMessage)
                        .matchAny(o -> { throw new MessageUnsupportedException(o); })
                        .build(),
                getContext()
        );
    }


    private void handleJsonObjectMessage(MessageDeserializer.StateChangeMessage message) {

        T response = respondMessage(message);

        if (response!=null)
            dispatcherRouter.tell(response, getSelf());
    }

    abstract T respondMessage(MessageDeserializer.StateChangeMessage message);


    public static class MessageUnsupportedException extends Exception {

        @Getter
        private Object messageObject;

        public MessageUnsupportedException(Object messageObject) {
            super("Unsupported message, class: "+ messageObject.getClass().getName());
            this.messageObject = messageObject;
        }
    }
}
