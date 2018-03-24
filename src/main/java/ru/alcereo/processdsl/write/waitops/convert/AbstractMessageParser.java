package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorRef;
import akka.event.LoggingReceive;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;
import static ru.alcereo.processdsl.Utils.success;


/**
 *
 */
public abstract class AbstractMessageParser<T> extends AbstractLoggingActor{

    private final ActorRef clientRef;
    private final ExecutionContext ds = getContext().dispatcher();

    public AbstractMessageParser(@NonNull ActorRef clientRef) {
        this.clientRef = clientRef;
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(MessageDeserializer.StringTransportMessage.class, this::handleJsonObjectMessage)
                        .build(),
                getContext()
        );
    }


    private void handleJsonObjectMessage(MessageDeserializer.StringTransportMessage message) {

        T response;
        final ActorRef sender = getSender();
        try {
            response = parseMessage(message);
        } catch (Exception e) {
            log().error(e, "Message parsing error: {}", message);
            sender.tell(
                    FailureResponse.builder()
                            .message(message)
                            .error(e)
                            .build(),
                    getSelf()
            );
            return;
        }

        Future<Object> clientRequestF = Patterns.ask(
                clientRef,
                response,
                Timeout.apply(5, TimeUnit.SECONDS)
        );

        clientRequestF.onSuccess(
                success(clientResponse -> {
                    log().debug( "Client: {} request success message: {}", clientResponse);

                    if (clientResponse instanceof ClientMessageSuccessResponse)
                        sender.tell(SuccessResponse.builder().message(message).build(), getSelf());

                    sender.tell(FailureResponse.builder().message(message).build(), getSelf());

                }),ds
        );

        clientRequestF.onFailure(
                failure(throwable -> {
                    log().error(throwable, "Client: {} request message error", clientRef);

                    sender.tell(
                            FailureResponse.builder()
                                    .message(message)
                                    .error(throwable)
                                    .build(),
                            getSelf()
                    );

                }),ds
        );
    }

    abstract T parseMessage(MessageDeserializer.StringTransportMessage message) throws Exception;

    @Value
    @Builder
    public static class ClientMessageSuccessResponse{
    }

    @Value
    @Builder
    public static class SuccessResponse{
        MessageDeserializer.StringTransportMessage message;
    }

    @Value
    @Builder
    public static class FailureResponse{
        MessageDeserializer.StringTransportMessage message;
        Throwable error;
    }

}
