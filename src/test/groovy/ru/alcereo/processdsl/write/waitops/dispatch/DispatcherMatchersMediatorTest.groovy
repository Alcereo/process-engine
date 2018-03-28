package ru.alcereo.processdsl.write.waitops.dispatch

import akka.routing.RoundRobinRoutingLogic
import akka.routing.Router
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest
import ru.alcereo.processdsl.write.waitops.parse.ParsedMessage
import scala.concurrent.duration.FiniteDuration

import java.util.concurrent.TimeUnit

class DispatcherMatchersMediatorTest extends ActorSystemInitializerTest {


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
                DispatcherMatchersMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMatchersMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        AbstractEventMatcher.ClientResponse.builder()
                        .msg(message)
                        .build()
                )
        }

        executorClientStub.expectMsgClass(DispatcherMatchersMediator.BroadcastingFinished)

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
                DispatcherMatchersMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMatchersMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        AbstractEventMatcher.ClientResponseFailure.builder()
                                .msg(message)
                                .build()
                )

                def matcherErrorMessage = managerStub.expectMsgClass(EventsDispatcher.MatcherClientError)
                assertEquals(
                        matcherErrorMessage.getMatcher(),
                        stub.getRef()
                )
        }

        executorClientStub.expectMsgClass(DispatcherMatchersMediator.BroadcastingFinished)

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
                DispatcherMatchersMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMatchersMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        AbstractEventMatcher.ClientResponseWithFinish.builder()
                                .clientPath(new TestKit(system).getRef().path())
                                .build()
                )

                def matcherErrorMessage = managerStub.expectMsgClass(EventsDispatcher.RemoveClientMatcherCmd)
                assertEquals(
                        matcherErrorMessage.getMatcher(),
                        stub.getRef()
                )
        }

        executorClientStub.expectMsgClass(DispatcherMatchersMediator.BroadcastingFinished)

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
                DispatcherMatchersMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMatchersMediator.StartBroadcastMessage.builder().build()
        )

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
        }

        matcherStubList.get(1).reply(
                AbstractEventMatcher.ClientResponse.builder()
                .msg(message)
                .build()
        )
        matcherStubList.remove(1)


        matcherStubList.get(5).reply(
                AbstractEventMatcher.ClientResponse.builder()
                        .msg(message)
                        .build()
        )
        matcherStubList.remove(5)


        def resultMessage = executorClientStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                DispatcherMatchersMediator.FailureResultBroadcasting
        )

        assertEquals(
                resultMessage.getMarchersWithoutAnswer().sort(),
                matcherStubList.collect{ it.getRef() }.sort()
        )

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testTestEmptyRouters() {

        def managerStub = new TestKit(system)
        def executorClientStub = new TestKit(system)

        Router router = new Router(new RoundRobinRoutingLogic())

        def message = new ParsedMessage(){}
        def mediatorActor = system.actorOf(
                DispatcherMatchersMediator.props(router, managerStub.getRef(), message),
                "mediator-actor"
        )

        executorClientStub.send(
                mediatorActor,
                DispatcherMatchersMediator.StartBroadcastMessage.builder().build()
        )

        executorClientStub.expectMsgClass(DispatcherMatchersMediator.BroadcastingFinished)

        Thread.sleep(100)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }
}
