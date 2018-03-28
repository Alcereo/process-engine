package ru.alcereo.processdsl.write.waitops.dispatch;

import akka.actor.ActorPath;
import akka.actor.Props;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;
import ru.alcereo.processdsl.write.waitops.parse.DeviceStateMessageParser;

public class DeviceFineStateMatcher extends AbstractEventDispatcherMatcher<DeviceStateMessageParser.DeviceStateChangeMessage, DeviceFineStateMatcher.DeviceStateFineEvent> {

    private String deviceId;

    public static Props props(
            ActorPath clientPath,
            Class<DeviceStateMessageParser.DeviceStateChangeMessage> messageClass,
            String deviceId
    ){
        return Props.create(
                DeviceFineStateMatcher.class,
                () -> new DeviceFineStateMatcher(clientPath, messageClass, deviceId)
        );
    }

    @Builder(builderMethodName = "buildStrategy")
    public static EventsDispatcher.MatcherStrategy strategy(@NonNull String deviceId){
        return clientPath ->
                props(
                        clientPath,
                        DeviceStateMessageParser.DeviceStateChangeMessage.class,
                        deviceId
                );
    }

    public DeviceFineStateMatcher(ActorPath clientPath, Class<DeviceStateMessageParser.DeviceStateChangeMessage> messageClass, String deviceId) {
        super(clientPath, messageClass);
        this.deviceId = deviceId;
    }

    @Override
    boolean eventMatches(DeviceStateMessageParser.DeviceStateChangeMessage msg) {
        return msg.getAtmId().equals(deviceId) && msg.getState().equals("FINE");
    }

    @Override
    DeviceStateFineEvent buildResponseMessage(DeviceStateMessageParser.DeviceStateChangeMessage msg) {
        return DeviceStateFineEvent.builder().build();
    }

    @Value
    @Builder
    public static class DeviceStateFineEvent {
    }
}
