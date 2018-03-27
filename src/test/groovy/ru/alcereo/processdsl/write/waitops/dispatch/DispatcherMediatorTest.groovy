package ru.alcereo.processdsl.write.waitops.dispatch

import akka.routing.RoundRobinRoutingLogic
import akka.routing.Router
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class DispatcherMediatorTest extends ActorSystemInitializerTest {


    void testBroadcastAll() {

        def managerStub = new TestKit(system)
        def executorClientStub = new TestKit(system)

        def matcherStubList = []
        Router router = new Router(new RoundRobinRoutingLogic())

        1.upto 50, {
            number ->
                def probe = new TestKit(system)
                matcherStubList << probe
                router = router.addRoutee(probe.getRef())
        }

        def message = new ParsedMessage(){}
        def mediatorActor = system.actorOf(
                DispatcherMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        AbstractEventDispatcherMatcher.ClientResponse.builder()
                        .msg(message)
                        .build()
                )
        }

        executorClientStub.expectMsgClass(DispatcherMediator.BroadcastingFinished)

        Thread.sleep(100)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testClientResponseFailure() {

        def managerStub = new TestKit(system)
        def executorClientStub = new TestKit(system)

        def matcherStubList = []
        Router router = new Router(new RoundRobinRoutingLogic())

        1.upto 50, {
            number ->
                def probe = new TestKit(system)
                matcherStubList << probe
                router = router.addRoutee(probe.getRef())
        }

        def message = new ParsedMessage(){}
        def mediatorActor = system.actorOf(
                DispatcherMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        AbstractEventDispatcherMatcher.ClientResponseFailure.builder()
                                .msg(message)
                                .build()
                )

                def matcherErrorMessage = managerStub.expectMsgClass(EventsDispatcher.MatcherClientError)
                assertEquals(
                        matcherErrorMessage.getMatcher(),
                        stub.getRef()
                )
        }

        executorClientStub.expectMsgClass(DispatcherMediator.BroadcastingFinished)

        Thread.sleep(100)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testClientResponseWithFinish() {

        def managerStub = new TestKit(system)
        def executorClientStub = new TestKit(system)

        def matcherStubList = []
        Router router = new Router(new RoundRobinRoutingLogic())

        1.upto 50, {
            number ->
                def probe = new TestKit(system)
                matcherStubList << probe
                router = router.addRoutee(probe.getRef())
        }

        def message = new ParsedMessage(){}
        def mediatorActor = system.actorOf(
                DispatcherMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        AbstractEventDispatcherMatcher.ClientResponseWithFinish.builder()
                                .msg(message)
                                .build()
                )

                def matcherErrorMessage = managerStub.expectMsgClass(EventsDispatcher.RemoveClientMatcherCmd)
                assertEquals(
                        matcherErrorMessage.getMatcher(),
                        stub.getRef()
                )
        }

        executorClientStub.expectMsgClass(DispatcherMediator.BroadcastingFinished)

        Thread.sleep(100)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testTimeOutResult() {

        def managerStub = new TestKit(system)
        def executorClientStub = new TestKit(system)

        List<TestKit> matcherStubList = []
        Router router = new Router(new RoundRobinRoutingLogic())

        1.upto 50, {
            number ->
                def probe = new TestKit(system)
                matcherStubList << probe
                router = router.addRoutee(probe.getRef())
        }

        def message = new ParsedMessage(){}
        def mediatorActor = system.actorOf(
                DispatcherMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
        }

        matcherStubList.get(1).reply(
                AbstractEventDispatcherMatcher.ClientResponse.builder()
                .msg(message)
                .build()
        )
        matcherStubList.remove(1)


        matcherStubList.get(5).reply(
                AbstractEventDispatcherMatcher.ClientResponse.builder()
                        .msg(message)
                        .build()
        )
        matcherStubList.remove(5)


        def resultMessage = executorClientStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                DispatcherMediator.FailureResultBroadcasting
        )

        assertEquals(
                resultMessage.getMarchersWithoutAnswer().sort(),
                matcherStubList.collect{ it.getRef() }.sort()
        )

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

}
