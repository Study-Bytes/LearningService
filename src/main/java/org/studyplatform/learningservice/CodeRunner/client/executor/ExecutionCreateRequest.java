package org.studyplatform.learningservice.CodeRunner.client.executor;

import java.util.List;
import java.util.Map;

public record ExecutionCreateRequest(
        String language,
        String code,
        List<TestInput> tests,
        ExecutionLimits limits,
        ExecutionPolicy executionPolicy,
        Map<String, Object> metadata
) {

    public record TestInput(
            String id,
            String input,
            Integer timeoutMs
    ) {
    }

    public record ExecutionLimits(
            Integer timeLimitMs,
            Integer memoryLimitMb,
            Integer outputLimitKb
    ) {
    }

    public record ExecutionPolicy(
            Boolean networkDisabled,
            Boolean readOnlyFs
    ) {
    }
}
