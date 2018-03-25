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

        managerStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )
        managerStub.expectMsgClass(DispatcherMediator.BroadcastStarting)

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        EventDispatcherMatcher.ClientResponse.builder()
                        .msg(message)
                        .build()
                )
        }

        managerStub.expectMsgClass(DispatcherMediator.SuccessResultBroadcasting)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testClientResponseFailure() {

        def managerStub = new TestKit(system)

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

        managerStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )
        managerStub.expectMsgClass(DispatcherMediator.BroadcastStarting)

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        EventDispatcherMatcher.ClientResponseFailure.builder()
                                .msg(message)
                                .build()
                )

                def matcherErrorMessage = managerStub.expectMsgClass(EventsDispatcher.MatcherClientError)
                assertEquals(
                        matcherErrorMessage.getMatcher(),
                        stub.getRef()
                )
        }

        managerStub.expectMsgClass(DispatcherMediator.SuccessResultBroadcasting)

        Thread.sleep(100)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testClientResponseWithFinish() {

        def managerStub = new TestKit(system)

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

        managerStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )
        managerStub.expectMsgClass(DispatcherMediator.BroadcastStarting)

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
                stub.reply(
                        EventDispatcherMatcher.ClientResponseWithFinish.builder()
                                .msg(message)
                                .build()
                )

                def matcherErrorMessage = managerStub.expectMsgClass(EventsDispatcher.RemoveMatcherCmd)
                assertEquals(
                        matcherErrorMessage.getMatcher(),
                        stub.getRef()
                )
        }

        managerStub.expectMsgClass(DispatcherMediator.SuccessResultBroadcasting)

        assertTrue(
                mediatorActor.isTerminated()
        )

    }

    void testTimeOutResult() {

        def managerStub = new TestKit(system)

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

        managerStub.send(
                mediatorActor,
                DispatcherMediator.StartBroadcastMessage.builder().build()
        )
        managerStub.expectMsgClass(DispatcherMediator.BroadcastStarting)

        matcherStubList.each {
            TestKit stub ->
                stub.expectMsg(message)
        }

        matcherStubList.get(1).reply(
                EventDispatcherMatcher.ClientResponse.builder()
                .msg(message)
                .build()
        )
        matcherStubList.remove(1)


        matcherStubList.get(5).reply(
                EventDispatcherMatcher.ClientResponse.builder()
                        .msg(message)
                        .build()
        )
        matcherStubList.remove(5)


        def resultMesage = managerStub.expectMsgClass(
                FiniteDuration.apply(6, TimeUnit.SECONDS),
                DispatcherMediator.FailureResultBroadcasting
        )

        assertEquals(
                resultMesage.getMarchersWithoutAnswer().sort(),
                matcherStubList.collect{ it.getRef() }.sort()
        )

        assertTrue(
                mediatorActor.isTerminated()
        )

    }
}
