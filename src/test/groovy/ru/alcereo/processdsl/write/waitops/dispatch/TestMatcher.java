package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.ActorPath;
import akka.actor.Props;
import lombok.Builder;
import lombok.NonNull;

import java.util.function.Function;


public class TestMatcher<T,R> extends AbstractEventMatcher<T, R> {

    private final Function<T, Boolean> eventMatches;
    private final Function<T, R> buildResponseMessageFrom;

    @Builder(builderMethodName = "propsBuilder")
    public static <T,R> Props props(@NonNull ActorPath clientPath,
                                    @NonNull Class<T> messageClass,
                                    @NonNull Function<T, Boolean> eventMatches,
                                    @NonNull Function<T, R> buildResponseMessage){

        return Props.create(TestMatcher.class, () ->
                new TestMatcher<>(clientPath, messageClass, eventMatches, buildResponseMessage)
        );
    }

    TestMatcher(ActorPath clientPath,
                Class<T> messageClass,
                Function<T, Boolean> eventMatches,
                Function<T, R> buildResponseMessageFrom) {

        super(clientPath, messageClass);
        this.buildResponseMessageFrom = buildResponseMessageFrom;
        this.eventMatches = eventMatches;
    }

    @Override
    public boolean eventMatches(T msg) {
        return eventMatches.apply(msg);
    }

    @Override
    public R buildResponseMessage(T msg) {
        return buildResponseMessageFrom.apply(msg);
    }

}
