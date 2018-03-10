package ru.alcereo.processdsl.domain

class AcceptResultOnFinishException extends Exception{
    AcceptResultOnFinishException() {
    }

    AcceptResultOnFinishException(String var1) {
        super(var1)
    }

    AcceptResultOnFinishException(String var1, Throwable var2) {
        super(var1, var2)
    }

    AcceptResultOnFinishException(Throwable var1) {
        super(var1)
    }

    AcceptResultOnFinishException(String var1, Throwable var2, boolean var3, boolean var4) {
        super(var1, var2, var3, var4)
    }
}
