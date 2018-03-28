package ru.alcereo.processdsl.write.process;

import akka.actor.Status;
import lombok.val;
import ru.alcereo.processdsl.domain.BusinessProcess;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

class ProcessInMemoryRepository extends ProcessRepositoryAbstractActor{

    private Set<BusinessProcess> processSet = new HashSet<>();

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(GetProcessByUID.class,       this::handleMessage)
                .match(GetProcessByTaskUid.class,   this::handleMessage)
                .match(UpdateProcess.class,         this::handleMessage)
                .match(AddProcess.class,            this::handleMessage)
                .matchAny(o -> log().error("Unhandled message: {}", o))
                .build();
    }

    private void handleMessage(GetProcessByTaskUid message) {
        log().debug("Get process by task: {}", message.getTaskUID());

        val processOpt = processSet.stream()
                .filter(process ->
                        process.containsTaskIdentifier(message.getTaskUID()))
                .findFirst();

        if (processOpt.isPresent())
            getSender().tell(processOpt.get(), getSelf());
        else
            getSender().tell(
                    new ProcessWithTaskNotFound(message.getTaskUID()),
                    getSelf()
            );

    }

    private void handleMessage(AddProcess message) {
        log().debug("Add process: {}", message);
        processSet.add(message.getProcess());
        getSender().tell(new Status.Success(message), getSelf());
    }

    private void handleMessage(UpdateProcess message) {
        log().debug("Update process: {}", message);

        processSet.add(message.getProcess());
        getSender().tell(new Status.Success(message), getSelf());
    }

    private void handleMessage(GetProcessByUID message) {
        log().debug("Get process: {}", message);
        Optional<BusinessProcess> processOpt = processSet.stream()
                .filter(businessProcess -> businessProcess.getIdentifier().equals(message.getProcessUID()))
                .findFirst();

        if (processOpt.isPresent())
            getSender().tell(processOpt.get(), getSelf());
        else
            getSender().tell(
                    new ProcessNotFound(message.getProcessUID()),
                    getSelf()
            );
    }

}
