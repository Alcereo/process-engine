package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.ActorRef;
import akka.actor.Props;
import lombok.Builder;
import lombok.NonNull;

import java.util.function.Function;

public class TestMessageParser<T> extends AbstractMessageParser<T> {

    @Builder(builderMethodName = "propsBuilder")
    public static <T> Props props(
            @NonNull ActorRef clientRef,
            @NonNull Function<MessageDeserializer.StringTransportMessage, T> parseFunction
    ){
        return Props.create(TestMessageParser.class, () -> new TestMessageParser<>(clientRef, parseFunction));
    }

    private final Function<MessageDeserializer.StringTransportMessage, T> function;

    private TestMessageParser(ActorRef clientRef,
                             Function<MessageDeserializer.StringTransportMessage, T> function) {
        super(clientRef);
        this.function = function;
    }

    @Override
    T parseMessage(MessageDeserializer.StringTransportMessage message) {
        return function.apply(message);
    }
}
