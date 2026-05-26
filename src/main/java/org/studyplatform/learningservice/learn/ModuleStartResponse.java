package org.studyplatform.learningservice.learn;

import java.time.LocalDateTime;

public record ModuleStartResponse(
        Long courseId,
        Long moduleId,
        LocalDateTime startedAt,
        boolean alreadyStarted
) {
}
