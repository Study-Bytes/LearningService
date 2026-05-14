package org.studyplatform.learningservice.CodeRunner.client.executor;

import java.util.Map;

public record ExecutionSessionCreateRequest(
        String language,
        String code,
        ExecutionCreateRequest.ExecutionLimits limits,
        ExecutionCreateRequest.ExecutionPolicy executionPolicy,
        Map<String, Object> metadata
) {
}
