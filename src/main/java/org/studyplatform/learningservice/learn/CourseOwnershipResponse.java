package org.studyplatform.learningservice.learn;

public record CourseOwnershipResponse(
        Long courseId,
        Long userId,
        Boolean owner
) {
}
