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
import ru.alcereo.processdsl.write.waitops.parse.exceptions.WrongResponseMessageType;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;
import static ru.alcereo.processdsl.Utils.success;

public class ParsingDispatcher extends AbstractLoggingActor{

    private final ExecutionContext ds = getContext().dispatcher();
    private final List<ParsingEntry> parsersMap = new ArrayList<>();

    private ParsingDispatcher(Map<MetadataMatcher, ParserActorCreatorWrapper> parsersConfig,
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

        return Props.create(ParsingDispatcher.class, () ->
                new ParsingDispatcher(parsersConfig, messagesConsumer)
        );
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(StringTransportMessage.class, this::handleTransportMessage)
                        .matchAny(o -> {
                            throw new WrongResponseMessageType(o.getClass());
                        })
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

    /*=======================================================*
     *                 TRANSPORT MESSAGES                    *
     *=======================================================*/

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

    /**
     * Not only parsing success, but also message dispatching
     */
    @Value
    @Builder
    public static class SuccessHandlingMessage{
        StringTransportMessage transportMessage;
    }

    /**
     * Parsing or message dispatching error
     */
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

}
