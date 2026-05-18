package org.studyplatform.learningservice.CodeRunner.api;

public record SubmissionTestResultResponse(
        String testKey,
        String visibility,
        Boolean passed,
        String actualOutput,
        String message,
        Long durationMs,
        Double memoryMb
) {
}
