package org.studyplatform.learningservice.moduleprogress;

import org.studyplatform.learningservice.common.ProgressStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ModuleProgressResponse(
        Long id,
        Long userId,
        Long courseId,
        Long moduleId,
        ProgressStatus status,
        BigDecimal progressPercent,
        Integer completedTasksCount,
        Integer totalTasksCount,
        Integer score,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime lastActivityAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ModuleProgressResponse fromEntity(ModuleProgress entity) {
        return new ModuleProgressResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getCourseId(),
                entity.getModuleId(),
                entity.getStatus(),
                entity.getProgressPercent(),
                entity.getCompletedTasksCount(),
                entity.getTotalTasksCount(),
                entity.getScore(),
                entity.getStartedAt(),
                entity.getCompletedAt(),
                entity.getLastActivityAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
