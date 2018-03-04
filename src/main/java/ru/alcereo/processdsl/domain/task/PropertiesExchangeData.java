package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

import java.util.List;

@Value
@Builder(builderClassName = "Builder")
public class PropertiesExchangeData {

    @NonNull
    protected List<PropMappingData> innerPropsFromContext;

    @NonNull
    protected List<PropMappingData> innerPropsFromLastOutput;

    @NonNull
    protected List<PropMappingData> outerPropsToContext;


    @Value
    public static class PropMappingData {
        String innerProp;
        String outerProp;
        public static PropMappingData of(String inner, String outer){
            return new PropMappingData(inner, outer);
        }
    }
}
