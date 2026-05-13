package org.studyplatform.learningservice.taskprogress;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.NotFoundException;

@Service
public class TaskProgressService {

    private final TaskProgressRepository repository;

    public TaskProgressService(TaskProgressRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public TaskProgressResponse create(TaskProgressCreateRequest request) {
        if (repository.existsByUserIdAndTaskId(request.getUserId(), request.getTaskId())) {
            throw new ConflictException("Task progress already exists for user_id and task_id");
        }

        TaskProgress progress = new TaskProgress();
        progress.setUserId(request.getUserId());
        progress.setCourseId(request.getCourseId());
        progress.setModuleId(request.getModuleId());
        progress.setTaskId(request.getTaskId());
        progress.setStatus(orDefaultStatus(request.getStatus()));
        progress.setAttemptsCount(orZeroInteger(request.getAttemptsCount()));
        progress.setBestScore(orZeroInteger(request.getBestScore()));
        progress.setLastScore(orZeroInteger(request.getLastScore()));
        progress.setIsCompleted(orFalse(request.getIsCompleted()));
        progress.setFirstOpenedAt(request.getFirstOpenedAt());
        progress.setStartedAt(request.getStartedAt());
        progress.setFirstSuccessAt(request.getFirstSuccessAt());
        progress.setCompletedAt(request.getCompletedAt());
        progress.setLastSubmissionAt(request.getLastSubmissionAt());
        progress.setLastActivityAt(request.getLastActivityAt());

        return TaskProgressResponse.fromEntity(repository.save(progress));
    }

    @Transactional
    public TaskProgressResponse update(
            Long userId,
            Long courseId,
            Long moduleId,
            Long taskId,
            TaskProgressUpdateRequest request
    ) {
        TaskProgress progress = repository.findByUserIdAndCourseIdAndModuleIdAndTaskId(userId, courseId, moduleId, taskId)
                .orElseThrow(() -> new NotFoundException("Task progress not found"));

        if (request.getStatus() != null) {
            progress.setStatus(request.getStatus());
        }
        if (request.getAttemptsCount() != null) {
            progress.setAttemptsCount(request.getAttemptsCount());
        }
        if (request.getBestScore() != null) {
            progress.setBestScore(request.getBestScore());
        }
        if (request.getLastScore() != null) {
            progress.setLastScore(request.getLastScore());
        }
        if (request.getIsCompleted() != null) {
            progress.setIsCompleted(request.getIsCompleted());
        }
        if (request.getFirstOpenedAt() != null) {
            progress.setFirstOpenedAt(request.getFirstOpenedAt());
        }
        if (request.getStartedAt() != null) {
            progress.setStartedAt(request.getStartedAt());
        }
        if (request.getFirstSuccessAt() != null) {
            progress.setFirstSuccessAt(request.getFirstSuccessAt());
        }
        if (request.getCompletedAt() != null) {
            progress.setCompletedAt(request.getCompletedAt());
        }
        if (request.getLastSubmissionAt() != null) {
            progress.setLastSubmissionAt(request.getLastSubmissionAt());
        }
        if (request.getLastActivityAt() != null) {
            progress.setLastActivityAt(request.getLastActivityAt());
        }

        return TaskProgressResponse.fromEntity(repository.save(progress));
    }

    @Transactional(readOnly = true)
    public TaskProgressResponse getByUserCourseModuleAndTask(
            Long userId,
            Long courseId,
            Long moduleId,
            Long taskId
    ) {
        TaskProgress progress = repository.findByUserIdAndCourseIdAndModuleIdAndTaskId(userId, courseId, moduleId, taskId)
                .orElseThrow(() -> new NotFoundException("Task progress not found"));
        return TaskProgressResponse.fromEntity(progress);
    }

    private ProgressStatus orDefaultStatus(ProgressStatus status) {
        return status == null ? ProgressStatus.NOT_STARTED : status;
    }

    private Integer orZeroInteger(Integer value) {
        return value == null ? 0 : value;
    }

    private Boolean orFalse(Boolean value) {
        return value == null ? Boolean.FALSE : value;
    }
}
