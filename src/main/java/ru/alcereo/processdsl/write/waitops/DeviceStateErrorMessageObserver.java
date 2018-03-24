package ru.alcereo.processdsl.write.waitops;

import akka.actor.Props;
import akka.routing.Router;
import lombok.Builder;
import lombok.Value;


public class DeviceStateErrorMessageObserver extends AbstractMessageEventObserver<DeviceStateErrorMessageObserver.DeviceStateErrorMessage> {

    public static Props props(Router dispatcherRouter) {
        return Props.create(DeviceStateErrorMessageObserver.class, () -> new DeviceStateErrorMessageObserver(dispatcherRouter));
    }

    public DeviceStateErrorMessageObserver(Router dispatcherRouter) {
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