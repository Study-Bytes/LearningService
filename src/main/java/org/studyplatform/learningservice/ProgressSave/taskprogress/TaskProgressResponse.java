package org.studyplatform.learningservice.taskprogress;

import org.studyplatform.learningservice.common.ProgressStatus;

import java.time.LocalDateTime;

public record TaskProgressResponse(
        Long id,
        Long userId,
        Long courseId,
        Long moduleId,
        Long taskId,
        ProgressStatus status,
        Integer attemptsCount,
        Integer bestScore,
        Integer lastScore,
        Boolean isCompleted,
        LocalDateTime firstOpenedAt,
        LocalDateTime startedAt,
        LocalDateTime firstSuccessAt,
        LocalDateTime completedAt,
        LocalDateTime lastSubmissionAt,
        LocalDateTime lastActivityAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static TaskProgressResponse fromEntity(TaskProgress entity) {
        return new TaskProgressResponse(
                entity.getId(),
                entity.getUserId(),
                entity.getCourseId(),
                entity.getModuleId(),
                entity.getTaskId(),
                entity.getStatus(),
                entity.getAttemptsCount(),
                entity.getBestScore(),
                entity.getLastScore(),
                entity.getIsCompleted(),
                entity.getFirstOpenedAt(),
                entity.getStartedAt(),
                entity.getFirstSuccessAt(),
                entity.getCompletedAt(),
                entity.getLastSubmissionAt(),
                entity.getLastActivityAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
