package org.studyplatform.learningservice.learn;

public record LearningItemStateResponse(
        Long courseId,
        Long itemId,
        LearningItemProgressResponse progress,
        LearningItemNavigationResponse navigation
) {
}
