package ru.alcereo.processdsl.write.waitops.convert

import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest

class MessageDeserializerTest extends ActorSystemInitializerTest {

    def messageText = getClass().getResource("/test-data/message-device-state-fine.json").text

    private TestKit producerStub
    private TestKit consumerStub
    private ActorRef deserializerActor

    @Override
    void setUp() {
        super.setUp()

        producerStub = new TestKit(system)
        consumerStub = new TestKit(system)


        def config = [:]

        deserializerActor = system.actorOf(MessageDeserializer.props(config))

    }

    void testSuccessParse() {

    }

    void testParseFailure() {

    }

    void testParserNotFoundTest() {

    }

}
