package org.studyplatform.learningservice.learn;

import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;

public record EnrollCourseResponse(
        Long courseId,
        ProgressStatus status,
        Integer progressPercent
) {
    public static EnrollCourseResponse fromEntity(CourseEnrollment enrollment) {
        return new EnrollCourseResponse(
                enrollment.getCourseId(),
                enrollment.getStatus(),
                enrollment.getProgressPercent().intValue()
        );
    }
}
