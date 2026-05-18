package org.studyplatform.learningservice.learn;

import org.studyplatform.learningservice.common.ProgressStatus;

import java.util.List;

public record LearningCourseStateResponse(
        Long courseId,
        Integer progressPercent,
        ProgressStatus enrollmentStatus,
        Long nextItemId,
        List<LearningCourseItemStateResponse> items
) {
}
