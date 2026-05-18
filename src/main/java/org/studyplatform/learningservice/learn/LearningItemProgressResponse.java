package org.studyplatform.learningservice.learn;

import org.studyplatform.learningservice.common.ProgressStatus;

public record LearningItemProgressResponse(
        ProgressStatus status,
        Integer attemptsCount,
        Integer lastScore
) {
}
