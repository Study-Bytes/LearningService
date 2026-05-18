package org.studyplatform.learningservice.learn;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;

import java.math.BigDecimal;

@Service
public class LearningEnrollmentService {

    private final CourseAvailabilityClient courseAvailabilityClient;
    private final CourseEnrollmentRepository courseEnrollmentRepository;

    public LearningEnrollmentService(
            CourseAvailabilityClient courseAvailabilityClient,
            CourseEnrollmentRepository courseEnrollmentRepository
    ) {
        this.courseAvailabilityClient = courseAvailabilityClient;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
    }

    @Transactional
    public EnrollCourseResponse enroll(Long userId, Long courseId) {
        CourseAvailabilityResponse availability = courseAvailabilityClient.getAvailability(courseId);
        if (availability == null || !Boolean.TRUE.equals(availability.availableForEnrollment())) {
            throw new ForbiddenException("Course is not available for enrollment");
        }

        if (courseEnrollmentRepository.existsByUserIdAndCourseId(userId, courseId)) {
            throw new ConflictException("User is already enrolled in course");
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setStatus(ProgressStatus.NOT_STARTED);
        enrollment.setProgressPercent(BigDecimal.ZERO);
        enrollment.setCompletedTasksCount(0);
        enrollment.setTotalTasksCount(0);
        enrollment.setTotalScore(0);

        return EnrollCourseResponse.fromEntity(courseEnrollmentRepository.save(enrollment));
    }
}
