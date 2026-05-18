package org.studyplatform.learningservice.learn;

public record LearningCourseItemStateResponse(
        Long itemId,
        Boolean completed,
        Boolean locked
) {
}
