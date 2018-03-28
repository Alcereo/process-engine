package ru.alcereo.processdsl.write.waitops.parse.exceptions;

import ru.alcereo.processdsl.write.waitops.parse.AbstractMessageParser;

public class WrongResponseMessageType extends Exception {

    private final Class messageClass;

    public WrongResponseMessageType(Class messageClass) {
        super("Get wrong request message type: "+messageClass.getName()+". Expected: " +
                ""+AbstractMessageParser.SuccessResponse.class.getName()+", " +
                "or "+AbstractMessageParser.FailureResponse.class.getName()
        );

        this.messageClass = messageClass;
    }

    public Class getMessageClass() {
        return messageClass;
    }
}
