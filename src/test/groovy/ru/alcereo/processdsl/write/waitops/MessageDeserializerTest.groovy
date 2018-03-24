package ru.alcereo.processdsl.write.waitops

import akka.actor.ActorRef
import akka.actor.OneForOneStrategy
import akka.actor.SupervisorStrategy
import akka.japi.pf.DeciderBuilder
import akka.routing.RoundRobinRoutingLogic
import akka.routing.Router
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest

class MessageDeserializerTest extends ActorSystemInitializerTest {

    def messageText = getClass().getResource("/test-data/message-device-state-fine.json").text

    void testMessageDeserializerBroadcast() {

        def prop1 = new TestKit(system)
        def prop2 = new TestKit(system)

        def router = new Router(new RoundRobinRoutingLogic())
                .addRoutee(prop1.getRef())
                .addRoutee(prop2.getRef())

        def messageFormatterActor = system.actorOf(MessageDeserializer.props(router), "deserializer")

        def message = MessageDeserializer.StringTransportMessage.builder()
                .metadata(
                    MessageDeserializer.MessageMetadata.builder()
                    .sender("device-module")
                    .type("state-change-message")
                    .build()
                ).message(messageText)
                .build()

        messageFormatterActor.tell message, ActorRef.noSender()

        prop1.expectMsgClass(MessageDeserializer.StateChangeMessage)
        prop2.expectMsgClass(MessageDeserializer.StateChangeMessage)

    }

    void testParserNotFoundException() {

        def propSupervisor = new TestKit(system)

        def messageFormatterActor = propSupervisor.childActorOf(
                MessageDeserializer.props(new Router(new RoundRobinRoutingLogic())),
                "deserializer",
                new OneForOneStrategy(
                        DeciderBuilder
                                .matchAny({ e ->
                                    propSupervisor.getRef().tell(e, ActorRef.noSender())
                                    return SupervisorStrategy.stop()
                                })
                                .build()
                )
        )

        def message = MessageDeserializer.StringTransportMessage.builder().
                metadata(
                        MessageDeserializer.MessageMetadata.builder()
                                .sender("unknown")
                                .type("unknown")
                                .build()
                ).message(messageText)
                .build()

        messageFormatterActor.tell message, ActorRef.noSender()

        propSupervisor.expectMsgClass(MessageDeserializer.ParserNotFoundException)

    }

    void testParserParseException(){

        def propSupervisor = new TestKit(system)

        def messageFormatterActor = propSupervisor.childActorOf(
                MessageDeserializer.props(new Router(new RoundRobinRoutingLogic())),
                "deserializer",
                new OneForOneStrategy(
                        DeciderBuilder
                                .matchAny({ e ->
                            propSupervisor.getRef().tell(e, ActorRef.noSender())
                            return SupervisorStrategy.stop()
                        })
                                .build()
                )
        )

        def message = MessageDeserializer.StringTransportMessage.builder().
                metadata(
                        MessageDeserializer.MessageMetadata.builder()
                                .sender("device-module")
                                .type("state-change-message")
                                .build()
                ).message("as123")
                .build()

        messageFormatterActor.tell message, ActorRef.noSender()

        propSupervisor.expectMsgClass(MessageDeserializer.ParserParseException)

    }

}
