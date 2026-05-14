package org.studyplatform.learningservice.CodeRunner.api;

public record SubmissionCreateResponse(
        Long submissionId,
        String status,
        String verdict,
        Integer score,
        Integer passedTestsCount,
        Integer totalTestsCount,
        String executorRequestId
) {
}
