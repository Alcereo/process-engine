package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import lombok.Value;

import java.util.ArrayList;
import java.util.List;

@Value
@Builder(builderClassName = "Builder")
public class PropertiesExchangeData {

    List<PropMappingData> innerPropsFromContext;
    List<PropMappingData> innerPropsFromLastOutput;
    List<PropMappingData> outerPropsToContext;

    @Value
    public static class PropMappingData {
        String innerProp;
        String outerProp;
        public static PropMappingData of(String inner, String outer){
            return new PropMappingData(inner, outer);
        }
    }

    public static PropertiesExchangeData empty(){
        return builder()
                .innerPropsFromContext(new ArrayList<>())
                .innerPropsFromLastOutput(new ArrayList<>())
                .outerPropsToContext(new ArrayList<>())
                .build();
    }

    public static class Builder {
        private List<PropertiesExchangeData.PropMappingData> innerPropsFromContext = new ArrayList<>();
        private List<PropertiesExchangeData.PropMappingData> innerPropsFromLastOutput = new ArrayList<>();
        private List<PropertiesExchangeData.PropMappingData> outerPropsToContext = new ArrayList<>();

        Builder() {
        }

        public PropertiesExchangeData.Builder innerPropsFromContext(List<PropertiesExchangeData.PropMappingData> innerPropsFromContext) {
            this.innerPropsFromContext = innerPropsFromContext;
            return this;
        }

        public PropertiesExchangeData.Builder innerPropsFromLastOutput(List<PropertiesExchangeData.PropMappingData> innerPropsFromLastOutput) {
            this.innerPropsFromLastOutput = innerPropsFromLastOutput;
            return this;
        }

        public PropertiesExchangeData.Builder outerPropsToContext(List<PropertiesExchangeData.PropMappingData> outerPropsToContext) {
            this.outerPropsToContext = outerPropsToContext;
            return this;
        }


        public PropertiesExchangeData.Builder addInnerPropsFromContext(String innerProp, String outerProp) {
            this.innerPropsFromContext.add(PropMappingData.of(innerProp, outerProp));
            return this;
        }

        public PropertiesExchangeData.Builder addInnerPropsFromLastOutput(String innerProp, String outerProp) {
            this.innerPropsFromLastOutput.add(PropMappingData.of(innerProp, outerProp));
            return this;
        }

        public PropertiesExchangeData.Builder addOuterPropsToContext(String innerProp, String outerProp) {
            this.outerPropsToContext.add(PropMappingData.of(innerProp, outerProp));
            return this;
        }

        public PropertiesExchangeData build() {
            return new PropertiesExchangeData(this.innerPropsFromContext, this.innerPropsFromLastOutput, this.outerPropsToContext);
        }

        public String toString() {
            return "PropertiesExchangeData.Builder(innerPropsFromContext=" + this.innerPropsFromContext + ", innerPropsFromLastOutput=" + this.innerPropsFromLastOutput + ", outerPropsToContext=" + this.outerPropsToContext + ")";
        }
    }
}
