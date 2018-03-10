package ru.alcereo.processdsl.process;

import akka.actor.AbstractLoggingActor;
import lombok.Value;
import ru.alcereo.processdsl.domain.BusinessProcess;

import java.util.UUID;

public abstract class ProcessRepositoryAbstractActor extends AbstractLoggingActor {

    /**========================================*
     *                MESSAGES                 *
     *=========================================*/

    @Value
    public static class GetProcessByUID {
        UUID processUID;
    }

    @Value
    public static class UpdateProcess{
        BusinessProcess process;
    }

    @Value
    public static class AddProcess{
        BusinessProcess process;
    }

    @Value
    public static class ProcessNotFound{
        UUID processUID;
    }

    @Value
    public static class ProcessWithTaskNotFound{
        UUID taskUID;
    }

    @Value
    public static class GetProcessByTaskUid {
        UUID taskUID;
    }
}
