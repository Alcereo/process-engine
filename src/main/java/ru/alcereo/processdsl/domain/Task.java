package ru.alcereo.processdsl.domain;

import akka.actor.Props;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Value;
import scala.Tuple2;

import java.io.Serializable;
import java.util.*;

@Value
@EqualsAndHashCode(of = {"identifier"})
@AllArgsConstructor
public class Task implements Serializable {

    //    TODO: Поставить имутабельные коллекции!!

    UUID identifier;
    Props taskProp;
    Map<String, Object> properties;

    List<Tuple2<String, String>> innerPropsFromContext;
    List<Tuple2<String, String>> innerPropsFromLastOutput;
    List<Tuple2<String, String>> outerPropsToContext;

    public static Task buildEmpty(){
        return new Task(
                UUID.randomUUID(),
                Props.empty(),
                new HashMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }

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

    public Task setProperties(Map<String, Object> properties) {
        return new Task(
                identifier,
                taskProp,
                properties,
                innerPropsFromContext,
                innerPropsFromLastOutput,
                outerPropsToContext
        );
    }

}
