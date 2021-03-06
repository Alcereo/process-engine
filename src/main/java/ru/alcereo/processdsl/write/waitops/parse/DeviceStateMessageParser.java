package ru.alcereo.processdsl.write.waitops.parse;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.gson.Gson;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.UUID;

public class DeviceStateMessageParser extends AbstractMessageParser<DeviceStateMessageParser.DeviceStateChangeMessage> {

    public final static String MESSAGE_TYPE = "device-state-message";

    public DeviceStateMessageParser(ActorRef clientRef) {
        super(clientRef);
    }

    public static Props props(@NonNull ActorRef clientRef){
        return Props.create(DeviceStateMessageParser.class, () -> new DeviceStateMessageParser(clientRef));
    }

    @Override
    DeviceStateChangeMessage parseMessage(ParsingDispatcher.StringTransportMessage message) throws Exception {
        return new Gson().fromJson(message.getMessage(), DeviceStateChangeMessage.class);
    }

    public static ParsingDispatcher.MetadataMatcher getMatcher(){
        return metadata -> metadata.getType().equals(MESSAGE_TYPE);
    }

    @Value
    @Builder
    public static class DeviceStateChangeMessage implements ParsedMessage{
        @NonNull
        UUID id;
        @NonNull
        String atmId;
        String description;
        @NonNull
        String state;
    }

}
