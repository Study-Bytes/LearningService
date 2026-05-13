package org.studyplatform.learningservice.moduleprogress;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.NotFoundException;

import java.math.BigDecimal;

@Service
public class ModuleProgressService {

    private final ModuleProgressRepository repository;

    public ModuleProgressService(ModuleProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ModuleProgressResponse create(ModuleProgressCreateRequest request) {
        if (repository.existsByUserIdAndModuleId(request.getUserId(), request.getModuleId())) {
            throw new ConflictException("Запись module_progress уже существует для user_id и module_id");
        }

        ModuleProgress progress = new ModuleProgress();
        progress.setUserId(request.getUserId());
        progress.setCourseId(request.getCourseId());
        progress.setModuleId(request.getModuleId());
        progress.setStatus(orDefaultStatus(request.getStatus()));
        progress.setProgressPercent(orZeroDecimal(request.getProgressPercent()));
        progress.setCompletedTasksCount(orZeroInteger(request.getCompletedTasksCount()));
        progress.setTotalTasksCount(orZeroInteger(request.getTotalTasksCount()));
        progress.setScore(orZeroInteger(request.getScore()));
        progress.setStartedAt(request.getStartedAt());
        progress.setCompletedAt(request.getCompletedAt());
        progress.setLastActivityAt(request.getLastActivityAt());

        return ModuleProgressResponse.fromEntity(repository.save(progress));
    }

    @Transactional
    public ModuleProgressResponse update(
            Long userId,
            Long courseId,
            Long moduleId,
            ModuleProgressUpdateRequest request
    ) {
        ModuleProgress progress = repository.findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId)
                .orElseThrow(() -> new NotFoundException("Запись module_progress не найдена"));

        if (request.getStatus() != null) {
            progress.setStatus(request.getStatus());
        }
        if (request.getProgressPercent() != null) {
            progress.setProgressPercent(request.getProgressPercent());
        }
        if (request.getCompletedTasksCount() != null) {
            progress.setCompletedTasksCount(request.getCompletedTasksCount());
        }
        if (request.getTotalTasksCount() != null) {
            progress.setTotalTasksCount(request.getTotalTasksCount());
        }
        if (request.getScore() != null) {
            progress.setScore(request.getScore());
        }
        if (request.getStartedAt() != null) {
            progress.setStartedAt(request.getStartedAt());
        }
        if (request.getCompletedAt() != null) {
            progress.setCompletedAt(request.getCompletedAt());
        }
        if (request.getLastActivityAt() != null) {
            progress.setLastActivityAt(request.getLastActivityAt());
        }

        return ModuleProgressResponse.fromEntity(repository.save(progress));
    }

    @Transactional(readOnly = true)
    public ModuleProgressResponse getByUserCourseAndModule(Long userId, Long courseId, Long moduleId) {
        ModuleProgress progress = repository.findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId)
                .orElseThrow(() -> new NotFoundException("Запись module_progress не найдена"));
        return ModuleProgressResponse.fromEntity(progress);
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
