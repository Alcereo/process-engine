package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.AbstractLoggingActor;
import akka.actor.Props;
import akka.event.LoggingReceive;
import akka.routing.Broadcast;
import akka.routing.Router;
import com.google.gson.Gson;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class MessageDeserializer extends AbstractLoggingActor{

    private final Router messagesParsersRouter;
    private final Map<MessageMetadata, Parser> parsersMap = new HashMap<>();

    public MessageDeserializer(Router messagesParsersRouter) {
        this.messagesParsersRouter = messagesParsersRouter;

        parsersMap.put(
                MessageMetadata.builder()
                .sender("device-module")
                .type("state-change-message")
                .build(),
                message -> {
                    try {
                        return new Gson().fromJson(message.message, StateChangeMessage.class);
                    }catch (Exception e){
                        throw new ParserParseException(e);
                    }
                }
        );

    }

    public static Props props(Router messagesParsersRouter) {
        return Props.create(MessageDeserializer.class, () -> new MessageDeserializer(messagesParsersRouter));
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(StringTransportMessage.class, this::handleTransportMessage)
                        .build(),
                getContext()
        );
    }

    private void handleTransportMessage(StringTransportMessage msg) throws ParserNotFoundException, ParserParseException {

        Parser parser = parsersMap.get(msg.metadata);

        if (parser==null)
            throw new ParserNotFoundException(msg.metadata);

        Object object = parser.parseMessage(msg);

        messagesParsersRouter.route(new Broadcast(object), getSelf());
    }

    @Value
    @Builder
    public static class StringTransportMessage{
        @NonNull
        MessageMetadata metadata;
        @NonNull
        String message;
    }

    @Value
    @Builder
    public static class MessageMetadata{
        @NonNull
        String sender;
        @NonNull
        String type;
    }

    /*=======================================================*
     *                       MESSAGES                        *
     *=======================================================*/

    @Value
    @Builder
    public static class StateChangeMessage{
        @NonNull
        UUID id;
        @NonNull
        String atmId;
        String description;
        @NonNull
        String state;
    }

    /*=======================================================*
     *                       UTIL                            *
     *=======================================================*/

    @FunctionalInterface
    private interface Parser<T>{
        T parseMessage(StringTransportMessage message) throws ParserParseException;
    }

    public static class ParserParseException extends Exception {
        public ParserParseException() {
        }

        public ParserParseException(String message) {
            super(message);
        }

        public ParserParseException(String message, Throwable cause) {
            super(message, cause);
        }

        public ParserParseException(Throwable cause) {
            super(cause);
        }

        public ParserParseException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }

    public static class ParserNotFoundException extends Exception {
        private MessageMetadata metadata;

        public ParserNotFoundException(MessageMetadata metadata) {
            super("Not found parser for metadata: "+metadata);
            this.metadata = metadata;
        }

        public ParserNotFoundException() {
        }

        public ParserNotFoundException(String message) {
            super(message);
        }

        public ParserNotFoundException(String message, Throwable cause) {
            super(message, cause);
        }

        public ParserNotFoundException(Throwable cause) {
            super(cause);
        }

        public ParserNotFoundException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
            super(message, cause, enableSuppression, writableStackTrace);
        }
    }
}
