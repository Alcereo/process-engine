package ru.alcereo.processdsl.domain;

import akka.actor.Props;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import scala.Tuple2;

import java.io.Serializable;
import java.util.*;

@Data
@EqualsAndHashCode(of = {"identifier"})
@AllArgsConstructor
public class Task implements Serializable {

    //    TODO: Поставить имутабельные коллекции!!

    @Getter
    UUID identifier;
    Map<String, Object> properties;


    List<Tuple2<String, String>> innerPropsFromContext;
    List<Tuple2<String, String>> innerPropsFromLastOutput;
    List<Tuple2<String, String>> outerPropsToContext;

    TaskActorType type;

    public static Task buildEmpty(){
        return new Task(
                UUID.randomUUID(),
                new HashMap<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                Props::empty
        );
    }


    public Task setProperties(Map<String, Object> properties) {
        return new Task(
                identifier,
                properties,
                innerPropsFromContext,
                innerPropsFromLastOutput,
                outerPropsToContext,
                type
        );
    }

}
