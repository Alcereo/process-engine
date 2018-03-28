package ru.alcereo.processdsl.write.waitops.parse;

import akka.actor.*;
import akka.japi.pf.DeciderBuilder;
import akka.routing.RoundRobinPool;
import com.sun.istack.internal.NotNull;
import lombok.Builder;
import scala.concurrent.duration.Duration;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Class to build ParserActorCreatorWrapper with RoundRobinPool, and One-For-One supervisor
 * strategy with restart on failure
 */
public class PooledSupervisoredCreatorWrapper implements ParsingDispatcher.ParserActorCreatorWrapper {

    private final int workerNumber;
    private final Function<ActorRef, Props> propsCreator;
    private final int maxNrOfRetries;
    private final String nameString;

    @Builder
    public PooledSupervisoredCreatorWrapper(Integer workerNumber,
                                            @NotNull Function<ActorRef, Props> propsCreator,
                                            Integer maxNrOfRetries,
                                            String nameString) {

        this.workerNumber   = Optional.ofNullable(workerNumber).orElse(5);
        this.propsCreator   = propsCreator;
        this.maxNrOfRetries = Optional.ofNullable(maxNrOfRetries).orElse(3);
        this.nameString     = Optional.ofNullable(nameString).orElse(UUID.randomUUID().toString());
    }

    @Override
    public ActorRef buildRef(AbstractActor.ActorContext context, ActorRef messagesConsumer){

        return context.actorOf(
                new RoundRobinPool(workerNumber)
                        .withSupervisorStrategy(
                                new OneForOneStrategy(
                                        maxNrOfRetries,
                                        Duration.apply(5, TimeUnit.SECONDS),
                                        true,
                                        DeciderBuilder.matchAny(throwable -> SupervisorStrategy.restart()).build()
                                )
                        ).props(propsCreator.apply(messagesConsumer)),
                nameString
        );
    }
}
