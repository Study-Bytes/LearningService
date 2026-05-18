package org.studyplatform.learningservice.CodeRunner.api;

import java.time.LocalDateTime;

public record SubmissionHistoryItemResponse(
        Long id,
        Long itemId,
        SubmissionResultStatus status,
        Double score,
        Integer passedTests,
        Integer totalTests,
        LocalDateTime createdAt
) {
}
