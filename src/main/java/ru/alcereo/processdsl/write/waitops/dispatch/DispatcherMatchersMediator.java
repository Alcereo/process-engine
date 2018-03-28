package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.AbstractActorWithTimers;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.event.LoggingReceive;
import akka.routing.ActorRefRoutee;
import akka.routing.Broadcast;
import akka.routing.Router;
import lombok.Builder;
import lombok.Value;
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage;
import scala.concurrent.duration.FiniteDuration;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DispatcherMatchersMediator extends AbstractActorWithTimers {

    private Router workers;
    private Integer requestCounter;
    private final ActorRef manager;
    private ActorRef executorClient;
    private final Object message;
    private Map<ActorRef, Boolean> requestMap = new HashMap<>();

    private DispatcherMatchersMediator(Router workers, ActorRef manager, Object message) {

        this.workers = workers;
        this.requestCounter = workers.routees().size();
        this.manager = manager;
        this.message = message;

        workers.routees().foreach(v1 -> {
            requestMap.put(((ActorRefRoutee)v1).ref(), false);
            return null;
        });
    }

    public static Props props(Router workers, ActorRef manager, ParsedMessage message){
        return Props.create(DispatcherMatchersMediator.class, () -> new DispatcherMatchersMediator(workers, manager, message));
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(StartBroadcastMessage.class,                             this::handleBroadcasting)
                        .match(TimeoutTriggerMessage.class,                             this::handleTrigger)
                        .match(AbstractEventMatcher.MessageEmptyHandled.class,        this::handleMessageHandle)
                        .match(AbstractEventMatcher.ClientResponse.class,             this::handleClientResponse)
                        .match(AbstractEventMatcher.ClientResponseWithFinish.class,   this::handleClientResponseWithFinish)
                        .match(AbstractEventMatcher.ClientResponseFailure.class,      this::handleCliendResponseFailure)
                        .build(),
                getContext()
        );
    }

    private void handleTrigger(TimeoutTriggerMessage triggerMessage) {
        executorClient.tell(
                FailureResultBroadcasting.builder()
                        .marchersWithoutAnswer(
                                requestMap.entrySet().stream()
                                        .filter(actorRefBooleanEntry -> actorRefBooleanEntry.getValue().equals(false))
                                        .map(Map.Entry::getKey)
                                        .collect(Collectors.toList())
                        )
                        .build(),
                getSelf()
        );
        getContext().stop(getSelf());
    }

    private void handleBroadcasting(StartBroadcastMessage msg) {
        workers.route(new Broadcast(message), getSelf());
        executorClient = getSender();

        if (workers.routees().size()==0) {
            finishBroadcasting();
            return;
        }

        getTimers().startSingleTimer(
                "timeout-trigger",
                new TimeoutTriggerMessage(),
                FiniteDuration.apply(5, TimeUnit.SECONDS)
        );
    }

    private void handleClientResponseWithFinish(AbstractEventMatcher.ClientResponseWithFinish message) {
        manager.tell(
                EventsDispatcher.RemoveClientMatcherCmd.builder()
                        .matcher(getSender())
                        .clientPath(message.getClientPath())
                        .build(),
                getSelf()
        );

        countRequests(getSender());
    }

    private void handleCliendResponseFailure(AbstractEventMatcher.ClientResponseFailure message) {
        manager.tell(
                EventsDispatcher.MatcherClientError.builder()
                        .matcher(getSender())
                        .message(message)
                        .build(),
                getSelf()
        );

        countRequests(getSender());
    }

    private void handleClientResponse(AbstractEventMatcher.ClientResponse message) {
        countRequests(getSender());
    }

    private void handleMessageHandle(AbstractEventMatcher.MessageEmptyHandled message) {
        countRequests(getSender());
    }

    private void countRequests(ActorRef sender) {
        if (!requestMap.get(sender)){
            requestMap.put(sender, true);
            requestCounter--;
        }

        if (requestCounter==0){
            finishBroadcasting();
        }
    }

    private void finishBroadcasting(){
        executorClient.tell(new BroadcastingFinished(), getSelf());
        getContext().stop(getSelf());
    }

    @Value
    @Builder
    public static class StartBroadcastMessage {
    }

    @Value
    public static class BroadcastingFinished {
    }

    @Value
    @Builder
    public static class FailureResultBroadcasting {
        List<ActorRef> marchersWithoutAnswer;
    }

    @Value
    private class TimeoutTriggerMessage {
    }
}
