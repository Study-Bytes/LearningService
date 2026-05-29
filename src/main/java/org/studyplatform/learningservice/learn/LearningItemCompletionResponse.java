package org.studyplatform.learningservice.learn;

import org.studyplatform.learningservice.common.ProgressStatus;

import java.time.LocalDateTime;

public record LearningItemCompletionResponse(
        Long courseId,
        Long moduleId,
        Long itemId,
        boolean completed,
        LocalDateTime completedAt,
        ProgressStatus status
) {
}
