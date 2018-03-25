package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.AbstractLoggingActor;
import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.event.LoggingReceive;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.Builder;
import lombok.Value;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;
import static ru.alcereo.processdsl.Utils.success;

public abstract class EventDispatcherMatcher<M_TYPE, RESP_TYPE> extends AbstractLoggingActor{

    private final ActorPath clientPath;
    private Class<M_TYPE> messageClass;
    private final ExecutionContext ds = getContext().dispatcher();

    public EventDispatcherMatcher(ActorPath clientPath, Class<M_TYPE> messageClass) {
        this.clientPath = clientPath;
        this.messageClass = messageClass;
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(messageClass, this::handleMessage)
                        .matchAny(this::emptyHandler)
                        .build(),
                getContext()
        );
    }

    private void handleMessage(M_TYPE msg) {

        final ActorRef msgSender = getSender();

        if (eventMatches(msg)){
            log().debug("Event matches. Send request to client");

            Future<Object> clientResponseF = Patterns.ask(
                    getContext().actorSelection(clientPath),
                    buildResponseMessage(msg),
                    Timeout.apply(5, TimeUnit.SECONDS)
            );

            clientResponseF.onFailure(
                    failure(throwable -> {
                        log().error(throwable,"Client: {} response failure with message: {}."
                                ,clientPath, msg);

                        msgSender.tell(new ClientResponseFailure(msg), getSelf());
                    }), ds
            );

            clientResponseF.onSuccess(
                    success(response -> {
                        log().debug("Client: {} success response with: {}",clientPath, response);


                        if (response instanceof ClientResponseWithFinish)
                            msgSender.tell(new ClientResponseWithFinish(msg), getSelf());
                        else
                            msgSender.tell(new ClientResponse(msg), getSelf());
                    }),
                    ds
            );
        }else {
            emptyHandler(msg);
        }
    }

    private void emptyHandler(Object msg) {
        getSender().tell(new MessageEmptyHandled(msg), getSelf());
    }


    abstract boolean eventMatches(M_TYPE msg);

    abstract RESP_TYPE buildResponseMessage(M_TYPE msg);


    @Value
    @Builder
    public static class ClientResponseWithFinish{
        private final Object msg;
    }

    @Value
    @Builder
    public static class ClientResponse{
        private final Object msg;
    }

    @Value
    public static class MessageEmptyHandled {
        private final Object o;
    }

    @Value
    @Builder
    public static class ClientResponseFailure {
        private final Object msg;
    }
}
