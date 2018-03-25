package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.LoggingReceive;
import akka.persistence.AbstractPersistentActor;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage;
import scala.collection.immutable.IndexedSeq;

import java.util.ArrayList;

public class EventsDispatcher extends AbstractPersistentActor{

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private Router childs = new Router(new RoundRobinRoutingLogic(), new ArrayList<>());

    private final String persistentId;

    public EventsDispatcher(String persistentId) {
        this.persistentId = persistentId;
    }

    public static Props props(@NonNull String persistentId){
        return Props.create(EventsDispatcher.class,
                () -> new EventsDispatcher(persistentId)
        );
    }

    @Override
    public String persistenceId() {
        return persistentId;
    }

    @Override
    public Receive createReceiveRecover() {
        return LoggingReceive.create(
                receiveBuilder()
                        .build(),
                getContext()
        );
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(SubscribeRequestCmd.class,   this::handleSubscribeRequestMessage)
                        .match(RemoveMatcherCmd.class,      this::handleRemoveMatcher)
                        .match(MatcherClientError.class,    this::handleMatcherClientError)
                        .match(ParsedMessage.class,         this::handleParsedMessage)
                        .build(),
                getContext()
        );
    }

    private void handleRemoveMatcher(RemoveMatcherCmd cmd) {
        childs = childs.removeRoutee(cmd.getMatcher());
        cmd.getMatcher().tell(PoisonPill.getInstance(), getSelf());

    }

    private void handleMatcherClientError(MatcherClientError message) {
        getSelf().tell(
                RemoveMatcherCmd.builder().matcher(message.getMatcher()).build(),
                ActorRef.noSender()
        );
    }


    private void handleParsedMessage(ParsedMessage msg) {
        IndexedSeq<Routee> routees = childs.routees();
    }

    private void handleSubscribeRequestMessage(SubscribeRequestCmd msg) {

        persist(msg, param -> {

        });
    }



    @Value
    public static class SubscribeRequestCmd {
        MatchStrategy strategy;
    }


    public interface MatchStrategy{
    }

    @Value
    @Builder
    public static class RemoveMatcherCmd{
        ActorRef matcher;
    }

    @Value
    @Builder
    public static class MatcherClientError{
        ActorRef matcher;
        EventDispatcherMatcher.ClientResponseFailure message;
    }
}
