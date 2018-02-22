package ru.alcereo.processdsl.task

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.testkit.javadsl.TestKit
import com.typesafe.config.ConfigFactory
import cucumber.api.groovy.Hooks
import cucumber.api.groovy.RU

import static junit.framework.Assert.assertEquals

this.metaClass.mixin(Hooks)
this.metaClass.mixin(RU)

/**
 * Created by alcereo on 12.01.18.
 */
class CustomWorld {
    ActorSystem system
    ActorRef taskActor
}

World { new CustomWorld() }

Before{
    def config = ConfigFactory.load("test-config")
    system = ActorSystem.create("test-config", config)
}


Когда(~/^создается актор таски$/) { ->
    taskActor = system.actorOf(
            PersistFSMTaskTest.PrintTaskActor.props("actor-persist-id"),
            "task"
    )
}

Тогда(~/^состояние актора: (\w+)$/) { PersistFSMTask.TaskState testState ->
    def probe = new TestKit(system)

    taskActor.tell(new PersistFSMTask.GetTaskStateCmd(), probe.getRef())
    def state = probe.expectMsgClass(PersistFSMTask.TaskState.class)

    assertEquals(
            testState,
            state
    )
}