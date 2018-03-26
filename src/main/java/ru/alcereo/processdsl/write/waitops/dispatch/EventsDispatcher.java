package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.LoggingReceive;
import akka.persistence.AbstractPersistentActor;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Router;
import lombok.*;
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class EventsDispatcher extends AbstractPersistentActor{

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private Router matchersRouter = new Router(new RoundRobinRoutingLogic(), new ArrayList<>());

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

    private ClientsMatchersData clientsMatchersData = new ClientsMatchersData();

    @Override
    public Receive createReceiveRecover() {
        return LoggingReceive.create(
                receiveBuilder()
                        .match(AddClientMatcherEvt.class, this::recoverAddClientMatcherEvt)
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

                        .match(GetRoutersQuery.class,               this::queryGetRouters)
                        .match(GetClientsMatchersDataQuery.class,   this::queryGetClientsMatchersData)
                        .build(),
                getContext()
        );
    }


    /* ========================================= *
     *                 HANDLERS                  *
     * ========================================= */

    private void handleRemoveMatcher(RemoveMatcherCmd cmd) {
        matchersRouter = matchersRouter.removeRoutee(cmd.getMatcher());
        cmd.getMatcher().tell(PoisonPill.getInstance(), getSelf());

    }

    private void handleMatcherClientError(MatcherClientError message) {
        getSelf().tell(
                RemoveMatcherCmd.builder().matcher(message.getMatcher()).build(),
                ActorRef.noSender()
        );
    }

    private void handleParsedMessage(ParsedMessage msg) {
        throw new NotImplementedException();
    }

    private void handleSubscribeRequestMessage(SubscribeRequestCmd msg) {

        final ActorRef sender = getSender();

        Props matcherProps = msg.getStrategy().createProps(sender.path());

        val evt = AddClientMatcherEvt.builder()
                .clientPath(sender.path())
                .matcherProps(matcherProps)
                .build();

        ActorRef newMatcher = getContext().actorOf(matcherProps);
        matchersRouter = matchersRouter.addRoutee(newMatcher);

        log.debug("Matcher: {} created for client: {}", newMatcher, sender);

        persist(evt, event -> {
            clientsMatchersData.addClientMatcher(event);
            sender.tell(new SuccessCmd(), getSelf());
        });
    }

    @Value
    @Builder
    public static class SubscribeRequestCmd {
        MatcherStrategy strategy;
    }

    @Value
    @Builder
    public static class SuccessCmd{
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
        AbstractEventDispatcherMatcher.ClientResponseFailure message;
    }

    /* ========================================= *
     *                 RECOVERS                  *
     * ========================================= */

    private void recoverAddClientMatcherEvt(AddClientMatcherEvt evt) {

        ActorRef newMatcher = getContext().actorOf(evt.getMatcherProps());
        matchersRouter = matchersRouter.addRoutee(newMatcher);

        log.debug("Matcher: {} recovered for client: {}", newMatcher, evt.getClientPath());

        clientsMatchersData.addClientMatcher(evt);

    }


    /* ========================================= *
     *                   STATE                   *
     * ========================================= */

    @Data
    public static class ClientsMatchersData {
        private Map<ActorPath, Props> matchersMap = new HashMap<>();

        private ClientsMatchersData(Map<ActorPath, Props> matchersMap){
            this.matchersMap = matchersMap;
        }

        public ClientsMatchersData(){
        }

        public void addClientMatcher(AddClientMatcherEvt event) {
            matchersMap.put(event.clientPath, event.matcherProps);
        }

        public int size(){
            return matchersMap.size();
        }

        public Props getProps(ActorPath actorPath){
            return matchersMap.get(actorPath);
        }

        public ClientsMatchersData copy(){
            return new ClientsMatchersData(new HashMap<>(matchersMap));
        }
    }

    public interface MatcherStrategy {
        Props createProps(ActorPath clientPath);
    }

    @Value
    @Builder
    public static class AddClientMatcherEvt implements Serializable {

        @NonNull
        ActorPath clientPath;

        @NonNull
        Props matcherProps;
    }


    /* ========================================= *
     *                 QUERIES                   *
     * ========================================= */

    private void queryGetClientsMatchersData(GetClientsMatchersDataQuery query) {
        getSender().tell(
                clientsMatchersData.copy(),
                getSelf()
        );
    }

    private void queryGetRouters(GetRoutersQuery query) {
        getSender().tell(
                matchersRouter,
                getSelf()
        );
    }

    @Value
    @Builder
    public static class GetRoutersQuery {
    }

    @Value
    @Builder
    public static class GetClientsMatchersDataQuery {
    }

}
