package org.studyplatform.learningservice.CodeRunner.client.executor;

public record ExecutionTestRunRequest(
        String id,
        String input,
        Integer timeoutMs
) {
}
