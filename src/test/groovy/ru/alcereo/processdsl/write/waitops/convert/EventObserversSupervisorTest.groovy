package ru.alcereo.processdsl.write.waitops.convert

import akka.actor.ActorRef
import akka.actor.Kill
import akka.actor.PoisonPill
import akka.routing.Broadcast
import akka.routing.GetRoutees
import akka.routing.Routees
import akka.testkit.javadsl.TestKit
import ru.alcereo.processdsl.ActorSystemInitializerTest

class EventObserversSupervisorTest extends ActorSystemInitializerTest {


    void testInitialization() {

        def stubActor = new TestKit(system)
        def dispatcherRouter = new TestKit(system)
        def supervisorActor = system.actorOf(EventObserversSupervisor.props(dispatcherRouter.getRef()))

        supervisorActor.tell(new EventObserversSupervisor.GetObserversRouter(), stubActor.getRef())
        def router = stubActor.expectMsgClass(ActorRef)

        router.tell(GetRoutees.instance, stubActor.getRef())
        def routees = stubActor.expectMsgClass(Routees)

        assertTrue(
            routees.getRoutees().size() > 0
        )

    }

    void testSupervisionStrategy() {

        def stubActor = new TestKit(system)
        def dispatcherRouter = new TestKit(system)
        def supervisorActor = system.actorOf(
                EventObserversSupervisor.props(dispatcherRouter.getRef()),
                "observer-supervisor"
        )


        supervisorActor.tell(new EventObserversSupervisor.GetObserversRouter(), stubActor.getRef())
        def router = stubActor.expectMsgClass(ActorRef)

        log.info "Send unsupported message"

        router.tell(new Broadcast("Asd"), stubActor.getRef())
        dispatcherRouter.expectNoMsg()


        log.info "Send kill message"

        router.tell(new Broadcast(Kill.instance), stubActor.getRef())
        dispatcherRouter.expectNoMsg()


        log.info "Try to send correct data"

        router.tell(new Broadcast(
                MessageDeserializer
                        .StateChangeMessage.builder()
                        .state("FINE")
                        .atmId("1")
                        .id(UUID.randomUUID())
                        .build()),
                stubActor.getRef()
        )

        dispatcherRouter.expectMsgClass(DeviceStateFineMessageObserver.DeviceStateFineMessage)


        router.tell(new Broadcast(
                MessageDeserializer
                        .StateChangeMessage.builder()
                        .state("ERROR")
                        .atmId("1")
                        .id(UUID.randomUUID())
                        .build()),
                stubActor.getRef()
        )

        dispatcherRouter.expectMsgClass(DeviceStateErrorMessageObserver.DeviceStateErrorMessage)


        log.info "Kill supervisor"

        supervisorActor.tell(PoisonPill.instance, stubActor.getRef())
        Thread.sleep(1000)


        router.tell(new Broadcast(
                MessageDeserializer
                        .StateChangeMessage.builder()
                        .state("ERROR")
                        .atmId("1")
                        .id(UUID.randomUUID())
                        .build()),
                stubActor.getRef()
        )

        dispatcherRouter.expectNoMsg()

    }

}
