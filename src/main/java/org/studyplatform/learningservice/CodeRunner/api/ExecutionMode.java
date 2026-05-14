package org.studyplatform.learningservice.CodeRunner.api;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum ExecutionMode {
    BATCH,
    ONE_BY_ONE;

    @JsonCreator
    public static ExecutionMode fromValue(String value) {
        if (value == null || value.isBlank()) {
            return BATCH;
        }
        String normalized = value.trim().toUpperCase().replace('-', '_');
        if ("ONEBYONE".equals(normalized)) {
            return ONE_BY_ONE;
        }
        if ("ONE_BY_ONE".equals(normalized)) {
            return ONE_BY_ONE;
        }
        return valueOf(normalized);
    }
}
