package ru.alcereo.processdsl.write.waitops;

import akka.actor.Props;
import akka.routing.Router;
import lombok.Builder;
import lombok.Value;


public class DeviceStateFineMessageObserver extends AbstractMessageEventObserver<DeviceStateFineMessageObserver.DeviceStateFineMessage> {

    public static Props props(Router dispatcherRouter) {
        return Props.create(DeviceStateFineMessageObserver.class, () -> new DeviceStateFineMessageObserver(dispatcherRouter));
    }

    public DeviceStateFineMessageObserver(Router dispatcherRouter) {
        super(dispatcherRouter);
    }

    @Override
    public DeviceStateFineMessage respondMessage(MessageDeserializer.StateChangeMessage message) {

        if (message.getState().equals("FINE")) return
                DeviceStateFineMessage.builder()
                .atmId(message.getAtmId())
                .build();

        return null;
    }


    @Value
    @Builder
    public static class DeviceStateFineMessage{
        String atmId;
    }

}