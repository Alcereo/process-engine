package ru.alcereo.processdsl.write.waitops.parse;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingReceive;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;
import static ru.alcereo.processdsl.Utils.success;

public class MessageConverter extends AbstractLoggingActor{

    private final ExecutionContext ds = getContext().dispatcher();
    private final List<ParsingEntry> parsersMap = new ArrayList<>();

    public MessageConverter(Map<MetadataMatcher, ParserActorCreatorWrapper> parsersConfig,
                            ActorRef messagesConsumer) {

        parsersConfig.forEach((metadataMatcher, parserActorCreatorWrapper) ->
                parsersMap.add(
                        ParsingEntry.builder()
                                .matcher(metadataMatcher)
                                .parserRef(parserActorCreatorWrapper.buildRef(getContext(), messagesConsumer))
                                .build()
                )
        );

    }

    public static Props props(@NonNull Map<MetadataMatcher, ParserActorCreatorWrapper> parsersConfig,
                              @NonNull ActorRef messagesConsumer) {

        return Props.create(MessageConverter.class, () ->
                new MessageConverter(parsersConfig, messagesConsumer)
        );
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

    private void handleTransportMessage(StringTransportMessage message) {

        Optional<ParsingEntry> parsingEntryOpt = parsersMap.stream().filter(parsingEntry ->
                parsingEntry.matcher
                        .match(message.metadata)
        ).findFirst();

        ActorRef sender = getSender();

        if (!parsingEntryOpt.isPresent()) {
            sender.tell(
                    ParserNotFoundResult.builder()
                            .transportMessage(message)
                            .build(),
                    getSelf()
            );
        }else {
            ParsingEntry parsingEntry = parsingEntryOpt.get();

            Future<Object> parserRequestF = Patterns.ask(
                    parsingEntry.getParserRef(),
                    message,
                    Timeout.apply(5, TimeUnit.SECONDS)
            );


            parserRequestF.onSuccess(
                    success(parserResponse -> {
                        log().debug( "Parser: {} response message: {}",
                                parsingEntry.getParserRef(),
                                parserResponse
                        );

                        if (parserResponse instanceof AbstractMessageParser.SuccessResponse)
                            sender.tell(
                                    SuccessHandlingMessage.builder()
                                            .transportMessage(message)
                                            .build(),
                                    getSelf()
                            );
                        else if (parserResponse instanceof AbstractMessageParser.FailureResponse)
                            sender.tell(
                                    FailureHandlingMessage.builder()
                                            .transportMessage(message)
                                            .error(((AbstractMessageParser.FailureResponse) parserResponse).getError())
                                            .build(),
                                    getSelf()
                            );
                        else
                            sender.tell(
                                    FailureHandlingMessage.builder()
                                            .transportMessage(message)
                                            .error(new WrongResponseMessageType(parserResponse.getClass()))
                                            .build(),
                                    getSelf()
                            );
                    }),ds
            );

            parserRequestF.onFailure(
                    failure(throwable -> {
                        log().error(throwable, "Parser: {} request error",
                                parsingEntry.getParserRef()
                        );

                        sender.tell(
                                FailureHandlingMessage.builder()
                                        .transportMessage(message)
                                        .error(throwable)
                                        .build(),
                                getSelf()
                        );

                    }),ds
            );

        }
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
    public static class ParserNotFoundResult{
        StringTransportMessage transportMessage;
    }

    @Value
    @Builder
    public static class SuccessHandlingMessage{
        StringTransportMessage transportMessage;
    }

    @Value
    @Builder
    public static class FailureHandlingMessage{
        StringTransportMessage transportMessage;
        Throwable error;
    }

    /*=======================================================*
     *                       UTIL                            *
     *=======================================================*/

    @Value
    @Builder
    private static class ParsingEntry {
        MetadataMatcher matcher;
        ActorRef parserRef;
    }

    public interface MetadataMatcher {
        boolean match(MessageMetadata metadata);
    }

    public interface ParserActorCreatorWrapper {
        ActorRef buildRef(ActorContext context, ActorRef messagesConsumer);
    }

    public static class WrongResponseMessageType extends Exception {
        public WrongResponseMessageType(Class aClass) {
            super("Get wrong request message type: "+aClass.getName()+". Expected: " +
                    ""+AbstractMessageParser.SuccessResponse.class.getName()+", " +
                    "or "+AbstractMessageParser.FailureResponse.class.getName()
            );
        }
    }

}
