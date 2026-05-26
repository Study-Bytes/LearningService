package org.studyplatform.learningservice.learn;

import java.time.LocalDateTime;
import java.util.List;

public record ModuleDeadlineStateResponse(
        Long courseId,
        Long moduleId,
        LocalDateTime deadlineAt,
        LocalDateTime moduleCompletedAt,
        Boolean moduleCompletedBeforeDeadline,
        DeadlineStatus deadlineStatus,
        List<DeadlineTaskResponse> tasksCompletedBeforeDeadline,
        List<DeadlineTaskResponse> tasksCompletedAfterDeadline
) {
}
