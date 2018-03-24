package ru.alcereo.processdsl.write.waitops

import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.japi.pf.DeciderBuilder
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest

class DeviceStateErrorMessageObserverTest extends ActorSystemInitializerTest {

    void testDeviceStateFineMessage() {

        def prop = new TestKit(system)

        def deviceObserver = system.actorOf(
                DeviceStateErrorMessageObserver.props(prop.getRef()),
                "device-state-error-observer"
        )

        def message = MessageDeserializer.StateChangeMessage.builder()
                .id(UUID.randomUUID())
                .atmId("1")
                .description("Some desc")
                .state("ERROR")
                .build()

        deviceObserver.tell message, ActorRef.noSender()

        def responseMessage = prop.expectMsgClass(DeviceStateErrorMessageObserver.DeviceStateErrorMessage)

        assertEquals(
                "1",
                responseMessage.getAtmId()
        )

    }

    void testDeviceStateNoMessage() {

        def prop = new TestKit(system)

        def deviceObserver = system.actorOf(
                DeviceStateErrorMessageObserver.props(prop.getRef()),
                "device-state-error-observer"
        )

        def message = MessageDeserializer.StateChangeMessage.builder()
                .id(UUID.randomUUID())
                .atmId("1")
                .description("Some desc")
                .state("FINE")
                .build()

        deviceObserver.tell message, ActorRef.noSender()

        prop.expectNoMsg()

    }

    void testMessageUnsupportedException() {

        def propSupervisor = new TestKit(getSystem())

        ActorRef messageObserverActor = propSupervisor.childActorOf(
                DeviceStateErrorMessageObserver.props(new TestKit(system).getRef()),
                "message-observer",
                new OneForOneStrategy(
                        DeciderBuilder.matchAny({ e ->
                            propSupervisor.getRef().tell(e, ActorRef.noSender())
                            return SupervisorStrategy.stop()
                        }).build()
                )
        )

        messageObserverActor.tell(new Object(), ActorRef.noSender())

        propSupervisor.expectMsgClass(AbstractMessageEventObserver.MessageUnsupportedException.class)

    }

}
