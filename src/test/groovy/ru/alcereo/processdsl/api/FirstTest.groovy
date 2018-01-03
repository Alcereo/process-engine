package ru.alcereo.processdsl.api

import java.util.concurrent.CompletableFuture
import java.util.function.Supplier

import static java.util.concurrent.CompletableFuture.*

/**
 * Created by alcereo on 01.01.18.
 */
class FirstTest extends GroovyTestCase{

    void testSomeFirst(){

        def result = supplyAsync { ->
            printTask {
                text = "Some text"
            }
        } thenAcceptAsync { obj ->
            println obj
        }

        def suncResult = result.get()

//        printTask {
//            text = suncResult.get "Status"
//        }

    }

    static Map<String, Object> printTask(@DelegatesTo(PrintTask) Closure closure){
        def task = new PrintTask()

        closure.setDelegate(task)
        closure.call()

        task.execute()
    }

    static Map<String, Object> printTaskAsync(@DelegatesTo(PrintTask) Closure closure){
        def task = new PrintTask()

        closure.setDelegate(task)
        closure.call()

        task.execute()
    }

//    static <Result> CompletableFuture<Result> asyncTask(Closure<Result> closure){
//        CompletableFuture.<Result>supplyAsync(
//                new Supplier<Result>() {
//                    @Override
//                    Result get() {
//                        return closure.call()
//                    }
//                }
//        )
//    }

    static CompletableFuture<Map<String, Object>> asyncTask(Closure<Map<String, Object>> closure){
        CompletableFuture.<Map<String, Object>>supplyAsync(
                new Supplier<Map<String, Object>>() {
                    @Override
                    Map<String, Object> get() {
                        return closure.call()
                    }
                }
        )
    }

}

class PrintTask{
    String text

    Map<String, Object> execute(){
        println text
        [
                "Status": "Ok!"
        ]
    }
}
