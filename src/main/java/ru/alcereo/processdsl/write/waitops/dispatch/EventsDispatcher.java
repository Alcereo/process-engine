package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.ActorPath;
import akka.actor.ActorRef;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.event.LoggingReceive;
import akka.pattern.Patterns;
import akka.persistence.AbstractPersistentActor;
import akka.routing.RoundRobinRoutingLogic;
import akka.routing.Router;
import akka.util.Timeout;
import lombok.*;
import ru.alcereo.processdsl.write.waitops.parse.AbstractMessageParser;
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;
import static ru.alcereo.processdsl.Utils.success;

/**
 * Main class to subscribe client on getting concrete events and messages.
 *
 * Clients is subscribing using {@link SubscribeRequestCmd} command object that
 * contains {@link MatcherStrategy} functional interface realisation to create Matcher Actor Props
 */
public class EventsDispatcher extends AbstractPersistentActor{

    private LoggingAdapter log = Logging.getLogger(getContext().getSystem(), this);
    private ExecutionContext ds = getContext().dispatcher();

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


    private Router matchersRouter = new Router(new RoundRobinRoutingLogic(), new ArrayList<>());

    private ClientsMatchersData clientsMatchersData = new ClientsMatchersData();

    @Override
    public Receive createReceiveRecover() {
        return LoggingReceive.create(
                receiveBuilder()
//                        TODO: Add snapshot support
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
                        .match(RemoveClientMatcherCmd.class,      this::handleRemoveMatcher)
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

    private void handleRemoveMatcher(RemoveClientMatcherCmd cmd) {
        matchersRouter = matchersRouter.removeRoutee(cmd.getMatcher());
        cmd.getMatcher().tell(PoisonPill.getInstance(), getSelf());

        RemoveClientEvt evt = RemoveClientEvt.builder()
                .clientPath(cmd.clientPath)
                .build();

        persist(evt, event -> {
            clientsMatchersData.removeClientMatcher(event);
        });

    }

    private void handleMatcherClientError(MatcherClientError message) {
        getSelf().tell(
                RemoveClientMatcherCmd.builder().matcher(message.getMatcher()).build(),
                ActorRef.noSender()
        );
    }

    private void handleParsedMessage(ParsedMessage msg) {

        ActorRef sender = getSender();

        ActorRef mediator = getContext().actorOf(
                DispatcherMatchersMediator.props(matchersRouter, getSelf(), msg)
        );

        Future<Object> broadcastF = Patterns.ask(
                mediator,
                DispatcherMatchersMediator.StartBroadcastMessage.builder().build(),
                Timeout.apply(6, TimeUnit.SECONDS)
        );

        broadcastF.onSuccess(
                success(
                        message -> {

                            if (message instanceof DispatcherMatchersMediator.BroadcastingFinished)
                                sender.tell(
                                        AbstractMessageParser.ClientMessageSuccessResponse.builder()
                                                .build(),
                                        getSelf()
                                );
                            else if (message instanceof DispatcherMatchersMediator.FailureResultBroadcasting){

                                sender.tell(
                                        AbstractMessageParser.ClientMessageFailureResponse.builder()
                                                .error(new RuntimeException("Not all matchers answer on broadcasting"))
                                                .build(),
                                        getSelf()
                                );

//                                TODO: Need a strategy when mediator responding with timeout
//                                List<ActorRef> marchersWithoutAnswer = ((DispatcherMatchersMediator.FailureResultBroadcasting) message).getMarchersWithoutAnswer();
//
//                                marchersWithoutAnswer.forEach(
//                                        actorRef -> {
//                                            log.error("Actor matcher: {} timeout message broadcasting. Delete it.", actorRef);
//                                            getSelf().tell(
//                                                    RemoveClientMatcherCmd.builder()
//                                                            .matcher(actorRef)
//                                                            .build(),
//                                                    getSelf()
//                                            );
//                                        }
//                                );
                            }
                        }
                ),
                ds
        );

        broadcastF.onFailure(
                failure(
                     throwable -> {
                         sender.tell(
                                 AbstractMessageParser.ClientMessageFailureResponse.builder()
                                         .error(throwable)
                                         .build(),
                                 getSelf()
                         );
                     }
                ), ds
        );

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
            sender.tell(new SuccessCmdResponse(), getSelf());
        });
    }

    @Value
    @Builder
    public static class SubscribeRequestCmd {
        MatcherStrategy strategy;
    }

    @Value
    @Builder
    public static class SuccessCmdResponse {
    }

    @Value
    @Builder
    public static class RemoveClientMatcherCmd {
        @NonNull
        ActorRef matcher;
        @NonNull
        ActorPath clientPath;
    }

    @Value
    @Builder
    public static class MatcherClientError{
        ActorRef matcher;
        AbstractEventMatcher.ClientResponseFailure message;
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

        public void removeClientMatcher(RemoveClientEvt event) {
            matchersMap.remove(event.getClientPath());
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

    @Value
    @Builder
    public static class RemoveClientEvt implements Serializable {
        @NonNull
        ActorPath clientPath;
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
