package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.*;
import akka.event.LoggingReceive;
import akka.japi.pf.DeciderBuilder;
import akka.routing.RoundRobinGroup;
import lombok.NonNull;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class EventObserversSupervisor extends AbstractLoggingActor{

    private ActorRef dispatcherRouter;
    private ActorRef childRouter;

    public EventObserversSupervisor(ActorRef dispatcherRouter) {
        this.dispatcherRouter = Objects.requireNonNull(dispatcherRouter);
    }

    public static Props props(@NonNull ActorRef dispatcherRouter){

        return Props.create(EventObserversSupervisor.class,
                () -> new EventObserversSupervisor(
                        dispatcherRouter
                )
        );
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        List<ActorRef> childs = new ArrayList<>();
        childs.add(
                getContext().actorOf(
                        DeviceStateErrorMessageObserver.props(dispatcherRouter),
                        "error-message-observer"
                )
        );
        childs.add(
                getContext().actorOf(
                        DeviceStateFineMessageObserver.props(dispatcherRouter),
                        "fine-message-observer"
                )
        );

        List<String> paths = childs.stream()
                .map(actorRef -> actorRef.path().toStringWithoutAddress())
                .collect(Collectors.toList());

        childRouter = getContext().actorOf(new RoundRobinGroup(paths).props(),
                        "observer-childRouter");
    }

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(
                true,
                DeciderBuilder
                        .match(AbstractMessageEventObserver.MessageUnsupportedException.class, (AbstractMessageEventObserver.MessageUnsupportedException e) -> {
                            log().error(e, "Unsupported message type send to observer: {}. Message: {}",
                                    getSender(), e.getMessageObject()
                            );
                            return SupervisorStrategy.resume();
                        })
                        .matchAny(e -> {
                            log().error(e,"Error while executing in actor: {}", getSender());
                            return SupervisorStrategy.restart();
                        })
                        .build()
        );
    }

    @Override
    public Receive createReceive() {
        return LoggingReceive.create(
                receiveBuilder()
                .match(GetObserversRouter.class, this::getRouterMessage)
                .build(),
                getContext()
        );
    }

    private void getRouterMessage(GetObserversRouter msg) {
        getSender().tell(childRouter, getSelf());
    }

    @Value
    public static class GetObserversRouter {
    }
}
