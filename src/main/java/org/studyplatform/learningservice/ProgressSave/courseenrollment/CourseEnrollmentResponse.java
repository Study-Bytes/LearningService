package org.studyplatform.learningservice.courseenrollment;

import org.studyplatform.learningservice.common.ProgressStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CourseEnrollmentResponse(
        Long id,
        Long userId,
        Long courseId,
        ProgressStatus status,
        BigDecimal progressPercent,
        Integer completedTasksCount,
        Integer totalTasksCount,
        Integer totalScore,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime lastActivityAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CourseEnrollmentResponse fromEntity(CourseEnrollment entity) {
        return new CourseEnrollmentResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getCourseId(),
                entity.getStatus(),
                entity.getProgressPercent(),
                entity.getCompletedTasksCount(),
                entity.getTotalTasksCount(),
                entity.getTotalScore(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getLastActivityAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
