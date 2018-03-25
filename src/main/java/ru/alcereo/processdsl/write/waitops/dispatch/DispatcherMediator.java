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

public class DispatcherMediator extends AbstractActorWithTimers {

    private Router workers;
    private Integer requestCounter;
    private final ActorRef manager;
    private final ParsedMessage message;
    private Map<ActorRef, Boolean> requestMap = new HashMap<>();

    public DispatcherMediator(Router workers, ActorRef manager, ParsedMessage message) {
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
        return Props.create(DispatcherMediator.class, () -> new DispatcherMediator(workers, manager, message));
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(StartBroadcastMessage.class,                             this::handleBroadcasting)
                        .match(TimeoutTriggerMessage.class,                             this::handleTrigger)
                        .match(EventDispatcherMatcher.MessageEmptyHandled.class,        this::handleMessageHandle)
                        .match(EventDispatcherMatcher.ClientResponse.class,             this::handleClientResponse)
                        .match(EventDispatcherMatcher.ClientResponseWithFinish.class,   this::handleClientResponseWithFinish)
                        .match(EventDispatcherMatcher.ClientResponseFailure.class,      this::handleCliendResponseFailure)
                        .build(),
                getContext()
        );
    }

    private void handleTrigger(TimeoutTriggerMessage triggerMessage) {
        manager.tell(
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
        getSender().tell(new BroadcastStarting(), getSelf());

        getTimers().startSingleTimer(
                "timeout-trigger",
                new TimeoutTriggerMessage(),
                FiniteDuration.apply(5, TimeUnit.SECONDS)
        );
    }

    private void handleClientResponseWithFinish(EventDispatcherMatcher.ClientResponseWithFinish message) {
        manager.tell(
                EventsDispatcher.RemoveMatcherCmd.builder()
                .matcher(getSender())
                .build(),
                getSelf()
        );

        countRequests(getSender());
    }

    private void handleCliendResponseFailure(EventDispatcherMatcher.ClientResponseFailure message) {
        manager.tell(
                EventsDispatcher.MatcherClientError.builder()
                        .matcher(getSender())
                        .message(message)
                        .build(),
                getSelf()
        );

        countRequests(getSender());
    }

    private void handleClientResponse(EventDispatcherMatcher.ClientResponse message) {
        countRequests(getSender());
    }

    private void handleMessageHandle(EventDispatcherMatcher.MessageEmptyHandled message) {
        countRequests(getSender());
    }

    private void countRequests(ActorRef sender) {
        if (!requestMap.get(sender)){
            requestMap.put(sender, true);
            requestCounter--;
        }

        if (requestCounter==0){
            manager.tell(new SuccessResultBroadcasting(), getSelf());
            getContext().stop(getSelf());
        }
    }

    @Value
    @Builder
    public static class StartBroadcastMessage {
    }

    @Value
    public static class SuccessResultBroadcasting {
    }

    @Value
    @Builder
    public static class FailureResultBroadcasting {
        List<ActorRef> marchersWithoutAnswer;
    }

    @Value
    public static class BroadcastStarting {
    }

    @Value
    private class TimeoutTriggerMessage {
    }
}
