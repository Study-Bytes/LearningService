package org.studyplatform.learningservice.CodeRunner.client.executor;

import java.util.List;
import java.util.Map;

public record ExecutionResponse(
        String id,
        String status,
        String language,
        Integer durationMs,
        Integer peakMemoryMb,
        List<TestExecutionResult> tests,
        Map<String, Object> metadata
) {

    public record TestExecutionResult(
            String testId,
            String outcome,
            Integer exitCode,
            OutputBlob stdout,
            OutputBlob stderr,
            Integer durationMs,
            Integer memoryMb
    ) {
    }

    public record OutputBlob(
            String data,
            Boolean truncated
    ) {
    }
}
