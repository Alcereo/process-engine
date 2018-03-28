package ru.alcereo.processdsl.write.waitops.dispatch

import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Kill
import akka.actor.Props
import akka.routing.Router
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import ru.alcereo.processdsl.write.waitops.parse.AbstractMessageParser
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class EventsDispatcherTest extends ActorSystemInitializerTest {

    private TestKit stubClient
    private TestKit stubMessager
    private TestKit stubManager

    private ActorRef eventDispatcher

    @Override
    void setUp() {
        super.setUp()

        stubClient = new TestKit(system)
        stubMessager = new TestKit(system)
        stubManager = new TestKit(system)


        eventDispatcher = system.actorOf(
                EventsDispatcher.props("dispatcher"),
                "event-dispatcher"
        )
    }

    static class TestStringMatcher extends TestMatcher<StringMessage, String>{

        private TestStringMatcher(ActorPath clientPath) {
            super(clientPath,
                    StringMessage.class,
                    { StringMessage s -> true },
                    { StringMessage s -> s.message }
            )
        }

        static EventsDispatcher.MatcherStrategy strategy(){
            return {
                clientPath ->
                    Props.create(TestStringMatcher.class, clientPath)
            }
        }

        static class StringMessage implements ParsedMessage {
            String message

            StringMessage(String message) {
                this.message = message
            }
        }
    }

    void testClientSubscribe() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)

//      Данные клиента сохранились
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetClientsMatchersDataQuery.builder()
                        .build()
        )
        def matchersData = stubManager.expectMsgClass(EventsDispatcher.ClientsMatchersData.class)

        assertEquals(
                1,
                matchersData.size()
        )
        assertTrue(
                matchersData.getProps(stubClient.getRef().path()) != null
        )

//      Матчер в роутере появился
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetRoutersQuery.builder()
                        .build()
        )
        def matchersRouter = stubManager.expectMsgClass(Router)

        assertEquals(
                1,
                matchersRouter.routees().size()
        )

    }

    void testClientSubscribeAndCrush() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)

//      Убиваем диспетчера и запускаем заново

        eventDispatcher.tell(Kill.instance, ActorRef.noSender())

        Thread.sleep(500)

        eventDispatcher = system.actorOf(
                EventsDispatcher.props("dispatcher"),
                "event-dispatcher"
        )

//      Данные клиента сохранились
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetClientsMatchersDataQuery.builder()
                        .build()
        )
        def matchersData = stubManager.expectMsgClass(EventsDispatcher.ClientsMatchersData.class)

        assertEquals(
                1,
                matchersData.size()
        )
        assertTrue(
                matchersData.getProps(stubClient.getRef().path()) != null
        )

//      Матчер в роутере появился
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetRoutersQuery.builder()
                        .build()
        )
        def matchersRouter = stubManager.expectMsgClass(Router)

        assertEquals(
                1,
                matchersRouter.routees().size()
        )

    }

    void testClientSubscribeAndGetMessage() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)


        def testString = "Test message"
        stubMessager.send(
                eventDispatcher,
                new TestStringMatcher.StringMessage(testString)
        )


        def clientMessage = stubClient.expectMsgClass(String)
        assertEquals(
                testString,
                clientMessage
        )
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubMessager.expectMsgClass(AbstractMessageParser.ClientMessageSuccessResponse)

    }

    void testClientSubscribeAndGetTwoMessages() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)


        def testString = "Test message"

        //        First
        stubMessager.send(
                eventDispatcher,
                new TestStringMatcher.StringMessage(testString)
        )


        def clientMessage = stubClient.expectMsgClass(String)
        assertEquals(
                testString,
                clientMessage
        )
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubMessager.expectMsgClass(AbstractMessageParser.ClientMessageSuccessResponse)

        //        Second
        stubMessager.send(
                eventDispatcher,
                new TestStringMatcher.StringMessage(testString)
        )


        clientMessage = stubClient.expectMsgClass(String)
        assertEquals(
                testString,
                clientMessage
        )
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubMessager.expectMsgClass(AbstractMessageParser.ClientMessageSuccessResponse)

    }

    void testClientSubscribeCrushStandUpAndGetMessage() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)

        //      Убиваем диспетчера и запускаем заново

        eventDispatcher.tell(Kill.instance, ActorRef.noSender())

        Thread.sleep(500)

        eventDispatcher = system.actorOf(
                EventsDispatcher.props("dispatcher"),
                "event-dispatcher"
        )

        //      Получаем событие и отправляем клиенту

        def testString = "Test message"
        stubMessager.send(
                eventDispatcher,
                new TestStringMatcher.StringMessage(testString)
        )


        def clientMessage = stubClient.expectMsgClass(String)
        assertEquals(
                testString,
                clientMessage
        )
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        stubMessager.expectMsgClass(AbstractMessageParser.ClientMessageSuccessResponse)

    }

    void testClientSubscribeAndResponseWithFinish() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)


        def testString = "Test message"
        stubMessager.send(
                eventDispatcher,
                new TestStringMatcher.StringMessage(testString)
        )


        def clientMessage = stubClient.expectMsgClass(String)
        assertEquals(
                testString,
                clientMessage
        )
        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponseWithFinish.builder().build())


        stubMessager.expectMsgClass(AbstractMessageParser.ClientMessageSuccessResponse)


        Thread.sleep(200)

        //      Данные клиента очищены
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetClientsMatchersDataQuery.builder()
                        .build()
        )
        def matchersData = stubManager.expectMsgClass(EventsDispatcher.ClientsMatchersData.class)

        assertEquals(
                0,
                matchersData.size()
        )

        //      Матчер в роутере удален
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetRoutersQuery.builder()
                        .build()
        )
        def matchersRouter = stubManager.expectMsgClass(Router)

        assertEquals(
                0,
                matchersRouter.routees().size()
        )

    }

    void testClientSubscribeAndNotRespond() {

        EventsDispatcher.MatcherStrategy matcher = TestStringMatcher.strategy()

        stubClient.send(
                eventDispatcher,
                EventsDispatcher.SubscribeRequestCmd.builder()
                        .strategy(matcher)
                        .build()
        )
        stubClient.expectMsgClass(EventsDispatcher.SuccessCmd)


        def testString = "Test message"
        stubMessager.send(
                eventDispatcher,
                new TestStringMatcher.StringMessage(testString)
        )


        def clientMessage = stubClient.expectMsgClass(String)
        assertEquals(
                testString,
                clientMessage
        )
//        --- Don't respond ---
//        stubClient.reply(AbstractEventDispatcherMatcher.ClientResponse.builder().build())

        def clazz = stubMessager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractMessageParser.ClientMessageFailureResponse
        )

        Thread.sleep(1000)


        //      Данные клиента очищены
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetClientsMatchersDataQuery.builder()
                        .build()
        )
        def matchersData = stubManager.expectMsgClass(EventsDispatcher.ClientsMatchersData.class)

        assertEquals(
                1,
                matchersData.size()
        )

        //      Матчер в роутере удален
        stubManager.send(
                eventDispatcher,
                EventsDispatcher.GetRoutersQuery.builder()
                        .build()
        )
        def matchersRouter = stubManager.expectMsgClass(Router)

        assertEquals(
                1,
                matchersRouter.routees().size()
        )

    }

}
