package ru.alcereo.processdsl.write.waitops.convert

import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class AbstractMessageParserTest extends ActorSystemInitializerTest {

    private TestKit clientStub
    private ActorRef testParserActor
    private TestKit managerStub

    @Override
    void setUp() {
        super.setUp()

        clientStub = new TestKit(system)
        managerStub = new TestKit(system)

        def testParserProps = TestMessageParser.propsBuilder()
                .clientRef(clientStub.getRef())
                .parseFunction(
                { message ->
                    message.getMessage()
                }
        ).build()

        testParserActor = system.actorOf(testParserProps)
    }

    void testSuccessParseAndClientResponse() {

        managerStub.send(
                testParserActor,
                MessageConverter.StringTransportMessage.builder()
                .metadata(
                        MessageConverter.MessageMetadata.builder()
                        .type("type")
                        .sender("sender")
                        .build()
                ).message("Some message")
                .build()
        )

        clientStub.expectMsgClass(String)
        clientStub.reply(
                AbstractMessageParser.ClientMessageSuccessResponse.builder()
                        .build()
        )

        managerStub.expectMsgClass(AbstractMessageParser.SuccessResponse)

    }

    void testSuccessParseFailedClientResponse() {

        managerStub.send(
                testParserActor,
                MessageConverter.StringTransportMessage.builder()
                        .metadata(
                        MessageConverter.MessageMetadata.builder()
                                .type("type")
                                .sender("sender")
                                .build()
                ).message("Some message")
                        .build()
        )

        clientStub.expectMsgClass(String)

//       --- Don`t response ---
//        clientStub.reply(
//                AbstractMessageParser.ClientMessageSuccessResponse.builder()
//                        .build()
//        )

        managerStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractMessageParser.FailureResponse
        )

    }

    void testSuccessParseWrongTypeClientResponse() {

        managerStub.send(
                testParserActor,
                MessageConverter.StringTransportMessage.builder()
                        .metadata(
                        MessageConverter.MessageMetadata.builder()
                                .type("type")
                                .sender("sender")
                                .build()
                ).message("Some message")
                        .build()
        )

        clientStub.expectMsgClass(String)
//       --- Wrong response ---
        clientStub.reply(
                new Object()
        )

        managerStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractMessageParser.FailureResponse
        )

    }

    void testParseException() {

        def testParserProps = TestMessageParser.propsBuilder()
                .clientRef(clientStub.getRef())
                .parseFunction(
                { message ->
                    throw new Exception("Some parse exception")
                }
        ).build()

        testParserActor = system.actorOf(testParserProps)

        managerStub.send(
                testParserActor,
                MessageConverter.StringTransportMessage.builder()
                        .metadata(
                        MessageConverter.MessageMetadata.builder()
                                .type("type")
                                .sender("sender")
                                .build()
                ).message("Some message")
                        .build()
        )

        managerStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractMessageParser.FailureResponse
        )

    }
}
