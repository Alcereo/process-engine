package ru.alcereo.processdsl.domain

class AcceptResultOnFinish extends Exception{
    AcceptResultOnFinish() {
    }

    AcceptResultOnFinish(String var1) {
        super(var1)
    }

    AcceptResultOnFinish(String var1, Throwable var2) {
        super(var1, var2)
    }

    AcceptResultOnFinish(Throwable var1) {
        super(var1)
    }

    AcceptResultOnFinish(String var1, Throwable var2, boolean var3, boolean var4) {
        super(var1, var2, var3, var4)
    }
}
