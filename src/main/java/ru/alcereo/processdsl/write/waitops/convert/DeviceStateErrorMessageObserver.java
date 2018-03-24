package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.ActorRef;
import akka.actor.Props;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;


public class DeviceStateErrorMessageObserver extends AbstractMessageEventObserver<DeviceStateErrorMessageObserver.DeviceStateErrorMessage> {

    public static Props props(@NonNull ActorRef dispatcherRouter) {
        return Props.create(DeviceStateErrorMessageObserver.class, () -> new DeviceStateErrorMessageObserver(dispatcherRouter));
    }

    public DeviceStateErrorMessageObserver(ActorRef dispatcherRouter) {
        super(dispatcherRouter);
    }

    @Override
    public DeviceStateErrorMessage respondMessage(MessageDeserializer.StateChangeMessage message) {

        if (message.getState().equals("ERROR")) return
                DeviceStateErrorMessage.builder()
                .atmId(message.getAtmId())
                .build();

        return null;
    }


    @Value
    @Builder
    public static class DeviceStateErrorMessage {
        String atmId;
    }

}