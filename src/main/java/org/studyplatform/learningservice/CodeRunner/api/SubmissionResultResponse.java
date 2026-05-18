package org.studyplatform.learningservice.CodeRunner.api;

import java.time.LocalDateTime;
import java.util.List;

public record SubmissionResultResponse(
        Long id,
        Long itemId,
        SubmissionResultStatus status,
        Double score,
        Integer passedTests,
        Integer totalTests,
        String stdout,
        String stderr,
        List<SubmissionTestResultResponse> testResults,
        LocalDateTime createdAt
) {
}
