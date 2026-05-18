package org.studyplatform.learningservice.learn;

public record CourseAvailabilityResponse(
        Long courseId,
        String status,
        String accessType,
        Boolean enrollmentEnabled,
        Boolean availableForEnrollment
) {
}
