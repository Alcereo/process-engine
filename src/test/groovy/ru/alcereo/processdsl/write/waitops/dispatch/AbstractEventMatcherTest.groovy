package ru.alcereo.processdsl.write.waitops.dispatch

import akka.actor.ActorRef
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class AbstractEventMatcherTest extends ActorSystemInitializerTest {

    private TestKit stubClient
    private TestKit stubManager
    private String testSuccessString = "success"
    private String testResponse = "response-success"
    private ActorRef matcherActor

    @Override
    void setUp() {
        super.setUp()

        stubClient = new TestKit(system)
        stubManager = new TestKit(system)

        def matcherProps = TestMatcher.propsBuilder()
                .clientPath(stubClient.getRef().path())
                .messageClass(String)
                .eventMatches { evt -> evt.equals(this.testSuccessString) }
                .buildResponseMessage { evt -> this.testResponse }
                .build()

        matcherActor = system.actorOf(matcherProps, "test-matcher")

    }

    void testHandleEmpty() {

        matcherActor.tell("unexpected", stubManager.getRef())
        stubManager.expectMsgClass(AbstractEventMatcher.MessageEmptyHandled)

        stubClient.expectNoMsg()
    }

    void testHandleSuccessClientRequestSuccess() {

        stubManager.send(
                matcherActor,
                testSuccessString
        )

        stubClient.expectMsg(testResponse)
        stubClient.reply(
                AbstractEventMatcher.ClientResponse.builder()
                        .msg(testResponse)
                        .build()
        )

        stubManager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractEventMatcher.ClientResponse
        )
    }

    void testHandleSuccessClientResponsWithFinish() {

        matcherActor.tell(testSuccessString, stubManager.getRef())

        stubClient.expectMsg(testResponse)
        stubClient.reply(
                AbstractEventMatcher.ClientResponseWithFinish.builder()
                        .build()
        )


        stubManager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractEventMatcher.ClientResponseWithFinish
        )
    }

    void testHandleSuccessClientRequestError() {

        matcherActor.tell(testSuccessString, stubManager.getRef())

        stubClient.expectMsg(testResponse)
//       --- Don`t reply ---

        stubManager.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                AbstractEventMatcher.ClientResponseFailure
        )

    }
}
