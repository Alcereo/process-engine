package ru.alcereo.processdsl.write.waitops

import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import ru.alcereo.processdsl.write.waitops.dispatch.AbstractEventDispatcherMatcher
import ru.alcereo.processdsl.write.waitops.dispatch.DeviceFineStateMatcher
import ru.alcereo.processdsl.write.waitops.dispatch.EventsDispatcher
import ru.alcereo.processdsl.write.waitops.parse.CreateTicketMessageParser
import ru.alcereo.processdsl.write.waitops.parse.DeviceStateMessageParser
import ru.alcereo.processdsl.write.waitops.parse.MessageConverter
import ru.alcereo.processdsl.write.waitops.parse.PooledSupervisoredCreatorWrapper

class WaitingOperationsIntegrationTests extends ActorSystemInitializerTest {

    private TestKit stubClient
    private TestKit stubTransportActor

    private ActorRef converterActor
    private ActorRef eventDispatcher

    @Override
    void setUp() {
        super.setUp()

//        The client who will receive a message
        stubClient = new TestKit(system)

//       Transport-layer actor who will transmit a message
        stubTransportActor = new TestKit(system)


//        Dispatcher - he will subscribe client and will
//          be dispatching message to him
        eventDispatcher = system.actorOf(
                EventsDispatcher.props("dispatcher"),
                "event-dispatcher"
        )

//        Selecting in the settings of which of the parsers will work
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
                MessageConverter.props(config, eventDispatcher),
                "message-converter"
        )

    }

    def static subscribeClientToDeviceFineState(stubClient, eventDispatcher){
        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(
                        DeviceFineStateMatcher.buildStrategy()
                                .deviceId("1")
                                .build()
                ).build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)
    }

    def static sendDeviceStateFineMessage(stubTransportActor, converterActor){
        stubTransportActor.send(
                converterActor,
                MessageConverter.StringTransportMessage.builder()
                        .metadata(
                        MessageConverter.MessageMetadata.builder()
                                .type(DeviceStateMessageParser.MESSAGE_TYPE)
                                .sender("Some-service")
                                .build()
                ).message(messageDeviceStateFineJsonText)
                        .build()
        )

    }

    void testClientGetMessageWhenSubscribe() {

        subscribeClientToDeviceFineState stubClient, eventDispatcher

        sendDeviceStateFineMessage stubTransportActor, converterActor


        stubClient.expectMsgClass(DeviceFineStateMatcher.DeviceStateFineEvent)
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)

    }

    void testClientGetMessageOnlyWhenSubscribe() {

        sendDeviceStateFineMessage stubTransportActor, converterActor

        stubClient.expectNoMsg()

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)



        subscribeClientToDeviceFineState stubClient, eventDispatcher

        sendDeviceStateFineMessage stubTransportActor, converterActor

        stubClient.expectMsgClass(DeviceFineStateMatcher.DeviceStateFineEvent)
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)

    }

    void testClientGetOnlyOneMessageWhenReplayWithFinish() {

        subscribeClientToDeviceFineState stubClient, eventDispatcher

        sendDeviceStateFineMessage stubTransportActor, converterActor


        stubClient.expectMsgClass(DeviceFineStateMatcher.DeviceStateFineEvent)
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponseWithFinish.builder().build())

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)


        sendDeviceStateFineMessage stubTransportActor, converterActor

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)

        stubClient.expectNoMsg()

    }

    void testClientGetFewMessagesWhenDefaultReplay() {

        subscribeClientToDeviceFineState stubClient, eventDispatcher



        sendDeviceStateFineMessage stubTransportActor, converterActor

        stubClient.expectMsgClass(DeviceFineStateMatcher.DeviceStateFineEvent)
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)



        sendDeviceStateFineMessage stubTransportActor, converterActor

        stubClient.expectMsgClass(DeviceFineStateMatcher.DeviceStateFineEvent)
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubTransportActor.expectMsgClass(MessageConverter.SuccessHandlingMessage)

    }
}
