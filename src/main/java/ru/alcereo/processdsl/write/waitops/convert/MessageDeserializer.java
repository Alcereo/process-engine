package ru.alcereo.processdsl.write.waitops.convert;

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

public class MessageDeserializer extends AbstractLoggingActor{

    private final ExecutionContext ds = getContext().dispatcher();
    private final List<ParsingEntry> parsersMap = new ArrayList<>();

    public MessageDeserializer(Map<MetadataMatcher, ActorCreatorWrapper> parsersConfig,
                               ActorRef messagesConsumer) {

        parsersConfig.forEach((metadataMatcher, actorCreatorWrapper) ->
            parsersMap.add(
                    ParsingEntry.builder()
                            .matcher(metadataMatcher)
                            .parserRef(actorCreatorWrapper.buildRef(getContext(), messagesConsumer))
                            .build()
            )
        );

    }

    public static Props props(@NonNull Map<MetadataMatcher, ActorCreatorWrapper> parsersConfig,
                              @NonNull ActorRef messagesConsumer) {

        return Props.create(MessageDeserializer.class, () ->
                new MessageDeserializer(parsersConfig, messagesConsumer)
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
                            .transportMessage(message),
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
                        log().debug( "Parser: {} request success message: {}",
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
                        else
                            sender.tell(
                                    FailureHandlingMessage.builder()
                                            .transportMessage(message)
                                            .build(),
                                    getSelf()
                            );
                    }),ds
            );

            parserRequestF.onFailure(
                    failure(throwable -> {
                        log().error(throwable, "Parser: {} message parsing error",
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

//    @Value
//    @Builder
//    public static class StateChangeMessage{
//        @NonNull
//        UUID id;
//        @NonNull
//        String atmId;
//        String description;
//        @NonNull
//        String state;
//    }

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
    private class ParsingEntry {
        MetadataMatcher matcher;
        ActorRef parserRef;
    }

    public interface MetadataMatcher {
        boolean match(MessageMetadata metadata);
    }

    public interface ActorCreatorWrapper {
        ActorRef buildRef(ActorContext context, ActorRef messagesConsumer);
    }

}
