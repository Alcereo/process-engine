package ru.alcereo.processdsl.write.waitops;

import akka.actor.AbstractLoggingActor;
import akka.event.LoggingReceive;
import akka.routing.Router;


/**
 * - Ловить только основные паттерны и заворачивать в читаемый объект.
 * Более делатьные совпдаения будет искать клиент - таска
 *
 *
 */
public abstract class AbstractMessageEventObserver<T> extends AbstractLoggingActor{

    private final Router dispatcherRouter;

    public AbstractMessageEventObserver(Router dispatcherRouter) {
        this.dispatcherRouter = dispatcherRouter;
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
            dispatcherRouter.route(response, getSelf());
    }

    abstract T respondMessage(MessageDeserializer.StateChangeMessage message);


    public static class MessageUnsupportedException extends Exception {

        private Object object;

        public MessageUnsupportedException(Object object) {
            super("Unsupported message, class: "+object.getClass().getName());
            this.object = object;
        }

    }
}
