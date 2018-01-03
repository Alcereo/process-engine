package ru.alcereo.processdsl.taks;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.dispatch.Futures;
import akka.testkit.TestKit;
import com.google.common.collect.ImmutableMap;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static akka.pattern.Patterns.ask;

/**
 * Created by alcereo on 01.01.18.
 */
public class TaskTest {

    private ActorSystem system;

    @Before
    public void setUp() throws Exception {
        Config config = ConfigFactory.parseString("akka.loglevel = DEBUG");
        system = ActorSystem.create("test-config", config);

    }

    @After
    public void tearDown() throws Exception {
        system.terminate();
    }


    @Test
    public void firstSimplePipelineTest() throws InterruptedException {

        TestKit probe = new TestKit(system);

        ActorRef task = system.actorOf(Task.props());

        pipelineSample(task, system);

        Thread.sleep(1000);

    }


    public static void pipelineSample(ActorRef task, ActorSystem system) {

        Map<String, Object> context = new HashMap<>();

        Futures.future(() -> {
                    return (Map) ImmutableMap.of("text", "Some text to output");
                }, system.dispatcher()
        ).flatMap(v1 -> {

                    context.put("test", "Context text");

                    return ask(task,
                            new Task.TaskExecuteMessage(v1),
                            1000);
                }, system.dispatcher()
        ).flatMap(result -> {

                    Map<String, Object> resultProps = ((Task.SuccessExecuting) result).properties;
                    ImmutableMap.Builder<String, Object> innerPropsBuilder = ImmutableMap.builder();



                    innerPropsBuilder.putAll(resultProps);
                    innerPropsBuilder.put("test", context.get("test"));



                    return ask(
                            task,
                            new Task.TaskExecuteMessage(innerPropsBuilder.build()),
                            1000
                    );
                }
                , system.dispatcher()
        ).flatMap(result -> {

            Map<String, Object> resultProps = ((Task.SuccessExecuting) result).properties;
            ImmutableMap.Builder<String, Object> innerPropsBuilder = ImmutableMap.builder();


            innerPropsBuilder.putAll(resultProps);
//            innerPropsBuilder.put("test", context.get("test"));

            return ask(
                    task,
                    new Task.TaskExecuteMessage(innerPropsBuilder.build()),
                    1000
            );
        }, system.dispatcher());

    }

}