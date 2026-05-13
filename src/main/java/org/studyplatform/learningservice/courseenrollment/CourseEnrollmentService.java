package org.studyplatform.learningservice.courseenrollment;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.NotFoundException;

import java.math.BigDecimal;

@Service
public class CourseEnrollmentService {

    private final CourseEnrollmentRepository repository;

    public CourseEnrollmentService(CourseEnrollmentRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public CourseEnrollmentResponse create(CourseEnrollmentCreateRequest request) {
        if (repository.existsByUserIdAndCourseId(request.getUserId(), request.getCourseId())) {
            throw new ConflictException("Запись course_enrollments уже существует для user_id и course_id");
        }

        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(request.getUserId());
        enrollment.setCourseId(request.getCourseId());
        enrollment.setStatus(orDefaultStatus(request.getStatus()));
        enrollment.setProgressPercent(orZeroDecimal(request.getProgressPercent()));
        enrollment.setCompletedTasksCount(orZeroInteger(request.getCompletedTasksCount()));
        enrollment.setTotalTasksCount(orZeroInteger(request.getTotalTasksCount()));
        enrollment.setTotalScore(orZeroInteger(request.getTotalScore()));
        enrollment.setStartedAt(request.getStartedAt());
        enrollment.setCompletedAt(request.getCompletedAt());
        enrollment.setLastActivityAt(request.getLastActivityAt());

        return CourseEnrollmentResponse.fromEntity(repository.save(enrollment));
    }

    @Transactional
    public CourseEnrollmentResponse update(Long userId, Long courseId, CourseEnrollmentUpdateRequest request) {
        CourseEnrollment enrollment = repository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new NotFoundException("Запись course_enrollments не найдена"));

        if (request.getStatus() != null) {
            enrollment.setStatus(request.getStatus());
        }
        if (request.getProgressPercent() != null) {
            enrollment.setProgressPercent(request.getProgressPercent());
        }
        if (request.getCompletedTasksCount() != null) {
            enrollment.setCompletedTasksCount(request.getCompletedTasksCount());
        }
        if (request.getTotalTasksCount() != null) {
            enrollment.setTotalTasksCount(request.getTotalTasksCount());
        }
        if (request.getTotalScore() != null) {
            enrollment.setTotalScore(request.getTotalScore());
        }
        if (request.getStartedAt() != null) {
            enrollment.setStartedAt(request.getStartedAt());
        }
        if (request.getCompletedAt() != null) {
            enrollment.setCompletedAt(request.getCompletedAt());
        }
        if (request.getLastActivityAt() != null) {
            enrollment.setLastActivityAt(request.getLastActivityAt());
        }

        return CourseEnrollmentResponse.fromEntity(repository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public CourseEnrollmentResponse getByUserAndCourse(Long userId, Long courseId) {
        CourseEnrollment enrollment = repository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new NotFoundException("Запись course_enrollments не найдена"));
        return CourseEnrollmentResponse.fromEntity(enrollment);
    }

    private ProgressStatus orDefaultStatus(ProgressStatus status) {
        return status == null ? ProgressStatus.NOT_STARTED : status;
    }

    private BigDecimal orZeroDecimal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Integer orZeroInteger(Integer value) {
        return value == null ? 0 : value;
    }
}
