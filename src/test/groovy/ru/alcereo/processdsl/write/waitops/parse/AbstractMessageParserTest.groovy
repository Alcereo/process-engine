package ru.alcereo.processdsl.write.waitops.parse

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
                ParsingDispatcher.StringTransportMessage.builder()
                .metadata(
                        ParsingDispatcher.MessageMetadata.builder()
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

    void testSuccessParseClientNotResponse() {

        managerStub.send(
                testParserActor,
                ParsingDispatcher.StringTransportMessage.builder()
                        .metadata(
                        ParsingDispatcher.MessageMetadata.builder()
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

    void testSuccessParseClientFailureResponse() {

        managerStub.send(
                testParserActor,
                ParsingDispatcher.StringTransportMessage.builder()
                        .metadata(
                        ParsingDispatcher.MessageMetadata.builder()
                                .type("type")
                                .sender("sender")
                                .build()
                ).message("Some message")
                        .build()
        )

        clientStub.expectMsgClass(String)

        def error = new RuntimeException()
        clientStub.reply(
                AbstractMessageParser.ClientMessageFailureResponse.builder()
                        .error(error)
                        .build()
        )

        def response = managerStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractMessageParser.FailureResponse
        )

        assertEquals(
                error,
                response.error
        )

    }

    void testSuccessParseWrongTypeClientResponse() {

        managerStub.send(
                testParserActor,
                ParsingDispatcher.StringTransportMessage.builder()
                        .metadata(
                        ParsingDispatcher.MessageMetadata.builder()
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
                ParsingDispatcher.StringTransportMessage.builder()
                        .metadata(
                        ParsingDispatcher.MessageMetadata.builder()
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
