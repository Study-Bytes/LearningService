package org.studyplatform.learningservice.learn;

import java.time.LocalDateTime;

public record DeadlineTaskResponse(
        Long taskId,
        LocalDateTime completedAt
) {
}
