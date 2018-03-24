package ru.alcereo.processdsl.write.waitops.convert

import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.japi.pf.DeciderBuilder
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import ru.alcereo.processdsl.write.waitops.convert.AbstractMessageEventObserver
import ru.alcereo.processdsl.write.waitops.convert.DeviceStateFineMessageObserver
import ru.alcereo.processdsl.write.waitops.convert.MessageDeserializer

class DeviceStateFineMessageObserverTest extends ActorSystemInitializerTest {

    void testDeviceStateFineMessage() {

        def prop = new TestKit(system)

        def deviceObserver = system.actorOf(
                DeviceStateFineMessageObserver.props(prop.getRef()),
                "device-state-fine-observer"
        )

        def message = MessageDeserializer.StateChangeMessage.builder()
                .id(UUID.randomUUID())
                .atmId("1")
                .description("Some desc")
                .state("FINE")
                .build()

        deviceObserver.tell message, ActorRef.noSender()

        def responseMessage = prop.expectMsgClass(DeviceStateFineMessageObserver.DeviceStateFineMessage)

        assertEquals(
                "1",
                responseMessage.getAtmId()
        )

    }

    void testDeviceStateNoMessage() {

        def prop = new TestKit(system)

        def deviceObserver = system.actorOf(
                DeviceStateFineMessageObserver.props(prop.getRef()),
                "device-state-fine-observer"
        )

        def message = MessageDeserializer.StateChangeMessage.builder()
                .id(UUID.randomUUID())
                .atmId("1")
                .description("Some desc")
                .state("ERROR")
                .build()

        deviceObserver.tell message, ActorRef.noSender()

        prop.expectNoMsg()

    }

    void testMessageUnsupportedException() {

        def propSupervisor = new TestKit(getSystem())

        ActorRef messageObserverActor = propSupervisor.childActorOf(
                DeviceStateFineMessageObserver.props(new TestKit(system).getRef()),
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
