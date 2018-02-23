package ru.alcereo.processdsl.domain;

import akka.actor.Props;
import lombok.EqualsAndHashCode;
import lombok.Value;
import scala.Tuple2;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Value
@EqualsAndHashCode(of = {"identifier"})
public class TaskExecutionContext implements Serializable {
    UUID identifier;
    Props taskProp;
    List<Tuple2<String, String>> innerPropsFromContext;
    List<Tuple2<String, String>> innerPropsFromLastOutput;
    List<Tuple2<String, String>> outerPropsToContext;
}
