package ru.alcereo.processdsl.write.waitops.dispatch

import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class EventDispatcherMatcherTest extends ActorSystemInitializerTest {

    void testEmptyHandle() {

        def stubClient = new TestKit(system)
        def stubManager = new TestKit(system)

        def matcherProps = TestMatcher.propsBuilder()
                .clientPath(stubClient.getRef().path())
                .messageClass(String)
                .eventMatches { evt -> false }
                .buildResponseMessageFrom { evt -> evt }
                .build()

        def matcherActor = system.actorOf(matcherProps, "test-matcher")

        matcherActor.tell(new Object(), stubManager.getRef())
        stubManager.expectMsgClass(EventDispatcherMatcher.MessageEmptyHandled)

        matcherActor.tell("some string", stubManager.getRef())
        stubManager.expectMsgClass(EventDispatcherMatcher.MessageEmptyHandled)

    }

    void testClientResponse() {

        def stubClient = new TestKit(system)
        def stubManager = new TestKit(system)


        def testSuccessString = "success"
        def testResponse = "response-success"
        def matcherProps = TestMatcher.propsBuilder()
                .clientPath(stubClient.getRef().path())
                .messageClass(String)
                .eventMatches { evt -> evt.equals(testSuccessString) }
                .buildResponseMessageFrom { evt -> testResponse }
                .build()

        def matcherActor = system.actorOf(matcherProps, "test-matcher")

        log.info("Test handle empty")
        matcherActor.tell("unexpected", stubManager.getRef())
        stubManager.expectMsgClass(EventDispatcherMatcher.MessageEmptyHandled)


        log.info("Test handle success - client success - ClientResponse")
        matcherActor.tell(testSuccessString, stubManager.getRef())

        stubClient.expectMsg(testResponse)
        stubClient.reply(
                EventDispatcherMatcher.ClientResponse.builder()
                        .msg(testResponse)
                        .build()
        )

        stubManager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                EventDispatcherMatcher.ClientResponse
        )


        log.info("Test handle success - client success - ClientResponseWithFinish")
        matcherActor.tell(testSuccessString, stubManager.getRef())

        stubClient.expectMsg(testResponse)
        stubClient.reply(
                EventDispatcherMatcher.ClientResponseWithFinish.builder()
                        .msg(testResponse)
                        .build()
        )

        stubManager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                EventDispatcherMatcher.ClientResponseWithFinish
        )


        log.info("Test handle success - client falure")
        matcherActor.tell(testSuccessString, stubManager.getRef())

        stubClient.expectMsg(testResponse)

        stubManager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                EventDispatcherMatcher.ClientResponseFailure
        )


    }
}
