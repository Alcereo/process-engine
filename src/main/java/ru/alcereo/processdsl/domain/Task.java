package ru.alcereo.processdsl.domain;

import akka.actor.Props;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import ru.alcereo.processdsl.task.PersistFSMTask;
import scala.Tuple2;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Data
@EqualsAndHashCode(of = {"identifier"})
@AllArgsConstructor
public abstract class Task implements Serializable {

    //    TODO: Поставить имутабельные коллекции!!

    UUID identifier;
    Props taskProp;
    Map<String, Object> properties;

    List<Tuple2<String, String>> innerPropsFromContext;
    List<Tuple2<String, String>> innerPropsFromLastOutput;
    List<Tuple2<String, String>> outerPropsToContext;

    public abstract CompletableFuture<PersistFSMTask.TaskEvents> execute();

    public abstract CompletableFuture<Object> prepare();

//    public static <TASK extends Task> TASK buildEmpty(){
//        return (TASK) new Task(
//                UUID.randomUUID(),
//                Props.empty(),
//                new HashMap<>(),
//                new ArrayList<>(),
//                new ArrayList<>(),
//                new ArrayList<>()
//        );
//    }

    public Task(UUID identifier, Props taskProp, List<Tuple2<String, String>> innerPropsFromContext, List<Tuple2<String, String>> innerPropsFromLastOutput, List<Tuple2<String, String>> outerPropsToContext) {
        this(
                identifier,
                taskProp,
                new HashMap<>(),
                innerPropsFromContext,
                innerPropsFromLastOutput,
                outerPropsToContext
        );
    }

    public abstract <TASK extends Task> TASK setProperties(Map<String, Object> properties);

}
