package org.studyplatform.learningservice.learn;

import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;

public record MyCourseEnrollmentResponse(
        Long courseId,
        Integer progressPercent,
        ProgressStatus status,
        Long nextItemId
) {
    public static MyCourseEnrollmentResponse fromEntity(CourseEnrollment enrollment) {
        return new MyCourseEnrollmentResponse(
                enrollment.getCourseId(),
                enrollment.getProgressPercent().intValue(),
                enrollment.getStatus(),
                null
        );
    }
}
