package ru.alcereo.processdsl.write.task;

import akka.actor.Props;
import akka.dispatch.Futures;
import akka.pattern.Patterns;
import akka.util.Timeout;
import groovy.lang.Binding;
import groovy.lang.GroovyShell;
import lombok.val;
import org.codehaus.groovy.control.CompilerConfiguration;
import ru.alcereo.processdsl.domain.TaskActorType;
import scala.concurrent.Future;

import java.util.HashMap;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static ru.alcereo.processdsl.Utils.failure;

public class GroovyCalculationTaskActor extends PersistFSMTask {
    public GroovyCalculationTaskActor(UUID taskUid) {
        super(taskUid);
    }

    @Override
    public void handleExecution(TaskDataState taskStateData) {

        val ds = getContext().dispatcher();

        Object scriptObject = taskStateData.getProperties().get("script-property");

        if (scriptObject != null){
            String script = (String)scriptObject;

            Binding binding = new Binding();
            binding.setVariable("context", new HashMap<>(taskStateData.getProperties()));

            CompilerConfiguration config = new CompilerConfiguration();

            GroovyShell shell = new GroovyShell(binding, config);

            Future<Object> executionFuture = Futures.future(() -> shell.evaluate(script), ds);

            executionFuture.map(result -> (HashMap) binding.getProperty("context"), ds)
                    .map(AppendToContextCmd::new, ds)
                    .flatMap(appendToContextCmd -> {
                        log().debug("Append response to context: {}", appendToContextCmd);
                        return Patterns.ask(
                                getSelf(),
                                appendToContextCmd,
                                Timeout.apply(5, TimeUnit.SECONDS));
                    }, ds)
                    .flatMap(result -> {
                        log().debug("Send executing finish command to self()");
                        return Patterns.ask(
                                getSelf(),
                                new SuccessFinishExecutingCmd(),
                                Timeout.apply(5, TimeUnit.SECONDS));
                    }, ds)
                    .onFailure(
                            failure(throwable -> {
                                log().error(throwable, "Error execution task!!");

                                Patterns.ask(
                                        getSelf(),
                                        new ErrorExecutingCmd(throwable),
                                        Timeout.apply(5, TimeUnit.SECONDS)
                                ).onFailure(
                                        failure(throwable1 -> {
                                            throw new RuntimeException("Error execution!!");
                                        }), ds
                                );
                            }), ds);

        }else {
            Patterns.ask(
                    getSelf(),
                    new ErrorExecutingCmd(new RuntimeException("Property 'script-property' not set")),
                    Timeout.apply(5, TimeUnit.SECONDS)
            ).onFailure(
                    failure(throwable1 -> {
                        throw new RuntimeException("Error execution!!");
                    }), ds
            );
        }

    }

    public static TaskActorType getType(UUID taskUid){
        return () -> Props.create(GroovyCalculationTaskActor.class, () -> new GroovyCalculationTaskActor(taskUid));
    }
}
