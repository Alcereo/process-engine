package ru.alcereo.processdsl.domain.task;

import lombok.Builder;
import lombok.NonNull;

import java.util.Collections;
import java.util.HashMap;
import java.util.UUID;

public class ProcessSuccessResultTask extends ProcessResultTask {

    @Builder
    public ProcessSuccessResultTask(@NonNull UUID identifier) {
        super(
                identifier,
                new HashMap<>(),
                PropertiesExchangeData.builder()
                        .innerPropsFromContext(Collections.EMPTY_LIST)
                        .innerPropsFromLastOutput(Collections.EMPTY_LIST)
                        .outerPropsToContext(Collections.EMPTY_LIST)
                        .build(),
                () -> null);
    }

    @Override
    public boolean isSuccess() {
        return true;
    }
}
