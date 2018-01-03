package ru.alcereo.processdsl.process;

import akka.actor.AbstractActor;
import akka.actor.Props;

/**
 * Created by alcereo on 01.01.18.
 */
public class Process extends AbstractActor{

    public static Props props(){
        return Props.create(Process.class, Process::new);
    }

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .build();
    }
}
