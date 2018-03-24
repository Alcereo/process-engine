package ru.alcereo.processdsl.write.waitops.convert;

import akka.actor.ActorRef;
import akka.actor.Props;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;


public class DeviceStateFineMessageObserver extends AbstractMessageEventObserver<DeviceStateFineMessageObserver.DeviceStateFineMessage> {

    public static Props props(@NonNull ActorRef dispatcherRouter) {
        return Props.create(DeviceStateFineMessageObserver.class, () -> new DeviceStateFineMessageObserver(dispatcherRouter));
    }

    public DeviceStateFineMessageObserver(ActorRef dispatcherRouter) {
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