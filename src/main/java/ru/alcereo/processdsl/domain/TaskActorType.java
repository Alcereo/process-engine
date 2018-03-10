package ru.alcereo.processdsl.domain;

import akka.actor.Props;

public interface TaskActorType {
    Props getTaskActorProps();
}
