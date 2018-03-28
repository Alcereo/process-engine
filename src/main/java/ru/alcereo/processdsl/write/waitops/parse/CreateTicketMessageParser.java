package ru.alcereo.processdsl.write.waitops.parse;

import akka.actor.ActorRef;
import akka.actor.Props;
import com.google.gson.Gson;
import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.UUID;

public class CreateTicketMessageParser extends AbstractMessageParser<CreateTicketMessageParser.CreateTicketMessage> {

    public final static String MESSAGE_TYPE = "ticket-create-message";

    private CreateTicketMessageParser(ActorRef clientRef) {
        super(clientRef);
    }

    public static Props props(@NonNull ActorRef clientRef){
        return Props.create(CreateTicketMessageParser.class, () -> new CreateTicketMessageParser(clientRef));
    }

    @Override
    CreateTicketMessage parseMessage(ParsingDispatcher.StringTransportMessage message) throws Exception {
        return new Gson().fromJson(message.getMessage(), CreateTicketMessage.class);
    }

    public static ParsingDispatcher.MetadataMatcher getMatcher(){
        return metadata -> metadata.getType().equals(MESSAGE_TYPE);
    }

    @Value
    @Builder
    public static class CreateTicketMessage implements ParsedMessage{

        @NonNull
        UUID id;

        @NonNull
        String atmId;

        String description;

        @NonNull
        UUID userId;

        @NonNull
        UUID ticketId;

        @NonNull
        String state;
    }
}
