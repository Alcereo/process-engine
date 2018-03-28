package ru.alcereo.processdsl.write.waitops.parse

import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import com.google.gson.JsonSyntaxException
import ru.alcereo.processdsl.ActorSystemInitializerTest

class ParsingDispatcherTest extends ActorSystemInitializerTest {

    def messageText = getClass().getResource("/test-data/message-device-state-fine.json").text

    private TestKit producerStub
    private TestKit consumerStub
    private ActorRef converterActor

    @Override
    void setUp() {
        super.setUp()

        producerStub = new TestKit(system)
        consumerStub = new TestKit(system)

        def config = [:]

        config[CreateTicketMessageParser.getMatcher()] =
                PooledSupervisoredCreatorWrapper.builder()
                        .propsCreator(CreateTicketMessageParser.&props)
                        .nameString("ticket-parser")
                        .build()

        config[DeviceStateMessageParser.getMatcher()] =
                PooledSupervisoredCreatorWrapper.builder()
                        .propsCreator(DeviceStateMessageParser.&props)
                        .nameString("device-state-parser")
                        .build()

        converterActor = system.actorOf(
                ParsingDispatcher.props(config, consumerStub.getRef()),
                "message-converter"
        )

    }

    void testSuccessParse() {

        producerStub.send(
                converterActor,
                ParsingDispatcher.StringTransportMessage.builder().
                        metadata(
                                ParsingDispatcher.MessageMetadata.builder()
                                        .sender("device-service")
                                        .type(DeviceStateMessageParser.MESSAGE_TYPE)
                                        .build()
                        ).message(messageText)
                        .build()
        )

        def deviceStateChangeMessage = consumerStub.expectMsgClass(DeviceStateMessageParser.DeviceStateChangeMessage)
        consumerStub.reply(AbstractMessageParser.ClientMessageSuccessResponse.builder().build())

        assertEquals(
                UUID.fromString("f574dbf8-14d1-4fda-a175-9e9a9dda80aa"),
                deviceStateChangeMessage.getId()
        )

        assertEquals(
                "1",
                deviceStateChangeMessage.getAtmId()
        )

        assertEquals(
                "FINE",
                deviceStateChangeMessage.getState()
        )

        producerStub.expectMsgClass(ParsingDispatcher.SuccessHandlingMessage)


    }

    void testParseFailure() {

        producerStub.send(
                converterActor,
                ParsingDispatcher.StringTransportMessage.builder().
                        metadata(
                                ParsingDispatcher.MessageMetadata.builder()
                                        .sender("device-service")
                                        .type(DeviceStateMessageParser.MESSAGE_TYPE)
                                        .build()
                        ).message("asdf")
                        .build()
        )


        def falureMessage = producerStub.expectMsgClass(ParsingDispatcher.FailureHandlingMessage)
        assertTrue(
                falureMessage.error instanceof JsonSyntaxException
        )

        consumerStub.expectNoMsg()

    }

    void testParserNotFoundTest() {

        def transportMessage = ParsingDispatcher.StringTransportMessage.builder().
                metadata(
                        ParsingDispatcher.MessageMetadata.builder()
                                .sender("device-service")
                                .type("unsupported")
                                .build()
                ).message("asdf")
                .build()

        producerStub.send(
                converterActor,
                transportMessage
        )

        def resultMsg = producerStub.expectMsgClass(ParsingDispatcher.ParserNotFoundResult)
        assertEquals(
                resultMsg.transportMessage,
                transportMessage
        )

        consumerStub.expectNoMsg()
    }

}
