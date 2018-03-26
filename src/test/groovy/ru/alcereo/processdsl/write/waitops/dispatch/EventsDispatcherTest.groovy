package ru.alcereo.processdsl.write.waitops.dispatch

import akka.actor.ActorPath
import akka.actor.ActorRef
import akka.actor.Kill
import akka.actor.Props
import akka.routing.Router
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest

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

    static class TestStringMatcher extends TestMatcher<String, String>{

        private TestStringMatcher(ActorPath clientPath) {
            super(clientPath,
                    String.class,
                    { s -> true },
                    {s -> s})
        }

        static EventsDispatcher.MatcherStrategy strategy(){
            return {
                clientPath ->
                    Props.create(TestStringMatcher.class, clientPath)
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

}
