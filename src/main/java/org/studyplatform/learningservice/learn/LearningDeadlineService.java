package org.studyplatform.learningservice.learn;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class LearningDeadlineService {

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ModuleProgressRepository moduleProgressRepository;
    private final TaskProgressRepository taskProgressRepository;

    public LearningDeadlineService(
            CourseEnrollmentRepository courseEnrollmentRepository,
            ModuleProgressRepository moduleProgressRepository,
            TaskProgressRepository taskProgressRepository
    ) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.moduleProgressRepository = moduleProgressRepository;
        this.taskProgressRepository = taskProgressRepository;
    }

    @Transactional(readOnly = true)
    public ModuleDeadlineStateResponse getModuleDeadlineState(
            Long userId,
            Long courseId,
            Long moduleId,
            LocalDateTime deadlineAt
    ) {
        courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        ModuleProgress moduleProgress = moduleProgressRepository
                .findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId)
                .orElse(null);
        LocalDateTime moduleCompletedAt = moduleProgress == null ? null : moduleProgress.getCompletedAt();

        List<DeadlineTaskResponse> beforeDeadline = new ArrayList<>();
        List<DeadlineTaskResponse> afterDeadline = new ArrayList<>();

        taskProgressRepository.findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId)
                .stream()
                .filter(this::isCompleted)
                .map(this::toDeadlineTask)
                .filter(task -> task.completedAt() != null)
                .sorted(Comparator.comparing(DeadlineTaskResponse::completedAt)
                        .thenComparing(DeadlineTaskResponse::taskId))
                .forEach(task -> {
                    if (isBeforeOrAtDeadline(task.completedAt(), deadlineAt)) {
                        beforeDeadline.add(task);
                    } else {
                        afterDeadline.add(task);
                    }
                });

        return new ModuleDeadlineStateResponse(
                courseId,
                moduleId,
                deadlineAt,
                moduleCompletedAt,
                moduleCompletedAt == null ? null : isBeforeOrAtDeadline(moduleCompletedAt, deadlineAt),
                resolveDeadlineStatus(moduleCompletedAt, deadlineAt),
                beforeDeadline,
                afterDeadline
        );
    }

    private DeadlineStatus resolveDeadlineStatus(LocalDateTime completedAt, LocalDateTime deadlineAt) {
        if (completedAt != null) {
            return isBeforeOrAtDeadline(completedAt, deadlineAt)
                    ? DeadlineStatus.COMPLETED_ON_TIME
                    : DeadlineStatus.COMPLETED_LATE;
        }
        return LocalDateTime.now().isAfter(deadlineAt)
                ? DeadlineStatus.OVERDUE
                : DeadlineStatus.IN_PROGRESS_ON_TIME;
    }

    private DeadlineTaskResponse toDeadlineTask(TaskProgress progress) {
        LocalDateTime completedAt = progress.getCompletedAt() != null
                ? progress.getCompletedAt()
                : progress.getFirstSuccessAt();
        return new DeadlineTaskResponse(progress.getTaskId(), completedAt);
    }

    private boolean isCompleted(TaskProgress progress) {
        return Boolean.TRUE.equals(progress.getIsCompleted()) || progress.getStatus() == ProgressStatus.COMPLETED;
    }

    private boolean isBeforeOrAtDeadline(LocalDateTime value, LocalDateTime deadlineAt) {
        return !value.isAfter(deadlineAt);
    }
}
