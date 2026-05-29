package org.studyplatform.learningservice.learn;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.common.exception.NotFoundException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningContentCompletionService {

    private final CourseItemContentClient courseItemContentClient;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ModuleProgressRepository moduleProgressRepository;
    private final TaskProgressRepository taskProgressRepository;

    public LearningContentCompletionService(
            CourseItemContentClient courseItemContentClient,
            CourseEnrollmentRepository courseEnrollmentRepository,
            ModuleProgressRepository moduleProgressRepository,
            TaskProgressRepository taskProgressRepository
    ) {
        this.courseItemContentClient = courseItemContentClient;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.moduleProgressRepository = moduleProgressRepository;
        this.taskProgressRepository = taskProgressRepository;
    }

    @Transactional
    public LearningItemCompletionResponse completeContentItem(
            Long userId,
            Long courseId,
            Long itemId,
            String authorizationHeader
    ) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        CourseItemContentResponse item = courseItemContentClient.getCourseItemContent(itemId, authorizationHeader);
        validateContentItem(courseId, itemId, item);

        Long moduleId = item.moduleId();
        TaskProgress progress = taskProgressRepository.findByUserIdAndCourseIdAndTaskId(userId, courseId, itemId)
                .orElseGet(() -> createTaskProgress(userId, courseId, moduleId, itemId));
        if (!moduleId.equals(progress.getModuleId())) {
            progress.setModuleId(moduleId);
        }

        LocalDateTime now = LocalDateTime.now();
        markEnrollmentOnActivity(enrollment, now);
        markTaskCompleted(progress, now);

        courseEnrollmentRepository.save(enrollment);
        progress = taskProgressRepository.save(progress);

        ensureModuleProgress(userId, courseId, moduleId, now);
        recalculateModuleProgress(userId, courseId, moduleId, now);
        recalculateCourseProgress(userId, courseId, now);

        return new LearningItemCompletionResponse(
                courseId,
                moduleId,
                itemId,
                Boolean.TRUE.equals(progress.getIsCompleted()),
                progress.getCompletedAt(),
                progress.getStatus()
        );
    }

    private void validateContentItem(Long courseId, Long itemId, CourseItemContentResponse item) {
        if (item == null || item.courseId() == null || item.moduleId() == null || item.itemId() == null) {
            throw new IllegalStateException("CourseService returned incomplete item content");
        }
        if (!courseId.equals(item.courseId()) || !itemId.equals(item.itemId())) {
            throw new NotFoundException("Course item not found in course");
        }
        String itemType = item.itemType() == null ? "" : item.itemType().toUpperCase();
        if (!"THEORY".equals(itemType) && !"FILE".equals(itemType)) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Only THEORY and FILE items can be completed without executor");
        }
    }

    private TaskProgress createTaskProgress(Long userId, Long courseId, Long moduleId, Long itemId) {
        TaskProgress progress = new TaskProgress();
        progress.setUserId(userId);
        progress.setCourseId(courseId);
        progress.setModuleId(moduleId);
        progress.setTaskId(itemId);
        progress.setStatus(ProgressStatus.NOT_STARTED);
        progress.setAttemptsCount(0);
        progress.setBestScore(0);
        progress.setLastScore(0);
        progress.setIsCompleted(false);
        return progress;
    }

    private ModuleProgress createModuleProgress(Long userId, Long courseId, Long moduleId) {
        ModuleProgress progress = new ModuleProgress();
        progress.setUserId(userId);
        progress.setCourseId(courseId);
        progress.setModuleId(moduleId);
        progress.setStatus(ProgressStatus.NOT_STARTED);
        progress.setProgressPercent(BigDecimal.ZERO);
        progress.setCompletedTasksCount(0);
        progress.setTotalTasksCount(0);
        progress.setScore(0);
        return progress;
    }

    private void ensureModuleProgress(Long userId, Long courseId, Long moduleId, LocalDateTime now) {
        moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId)
                .orElseGet(() -> {
                    ModuleProgress created = createModuleProgress(userId, courseId, moduleId);
                    created.setStartedAt(now);
                    created.setLastActivityAt(now);
                    return moduleProgressRepository.save(created);
                });
    }

    private void markEnrollmentOnActivity(CourseEnrollment enrollment, LocalDateTime now) {
        enrollment.setLastActivityAt(now);
        if (enrollment.getStartedAt() == null) {
            enrollment.setStartedAt(now);
        }
        if (enrollment.getStatus() == ProgressStatus.NOT_STARTED) {
            enrollment.setStatus(ProgressStatus.IN_PROGRESS);
        }
    }

    private void markTaskCompleted(TaskProgress progress, LocalDateTime now) {
        if (progress.getFirstOpenedAt() == null) {
            progress.setFirstOpenedAt(now);
        }
        if (progress.getStartedAt() == null) {
            progress.setStartedAt(now);
        }
        if (progress.getFirstSuccessAt() == null) {
            progress.setFirstSuccessAt(now);
        }
        if (progress.getCompletedAt() == null) {
            progress.setCompletedAt(now);
        }
        progress.setLastActivityAt(now);
        progress.setIsCompleted(true);
        progress.setStatus(ProgressStatus.COMPLETED);
    }

    private void recalculateModuleProgress(Long userId, Long courseId, Long moduleId, LocalDateTime now) {
        ModuleProgress moduleProgress = moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(
                        userId,
                        courseId,
                        moduleId
                )
                .orElseGet(() -> createModuleProgress(userId, courseId, moduleId));

        List<TaskProgress> tasks = taskProgressRepository.findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId);
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(task -> Boolean.TRUE.equals(task.getIsCompleted())).count();
        int score = tasks.stream().map(TaskProgress::getBestScore).mapToInt(this::orZero).sum();

        moduleProgress.setTotalTasksCount(total);
        moduleProgress.setCompletedTasksCount(completed);
        moduleProgress.setScore(score);
        moduleProgress.setProgressPercent(percent(completed, total));
        moduleProgress.setLastActivityAt(now);

        if (completed == 0) {
            moduleProgress.setStatus(ProgressStatus.NOT_STARTED);
        } else if (completed == total && total > 0) {
            moduleProgress.setStatus(ProgressStatus.COMPLETED);
            if (moduleProgress.getCompletedAt() == null) {
                moduleProgress.setCompletedAt(now);
            }
            if (moduleProgress.getStartedAt() == null) {
                moduleProgress.setStartedAt(now);
            }
        } else {
            moduleProgress.setStatus(ProgressStatus.IN_PROGRESS);
            if (moduleProgress.getStartedAt() == null) {
                moduleProgress.setStartedAt(now);
            }
        }

        moduleProgressRepository.save(moduleProgress);
    }

    private void recalculateCourseProgress(Long userId, Long courseId, LocalDateTime now) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        List<TaskProgress> tasks = taskProgressRepository.findByUserIdAndCourseId(userId, courseId);
        int total = tasks.size();
        int completed = (int) tasks.stream().filter(task -> Boolean.TRUE.equals(task.getIsCompleted())).count();
        int score = tasks.stream().map(TaskProgress::getBestScore).mapToInt(this::orZero).sum();

        enrollment.setTotalTasksCount(total);
        enrollment.setCompletedTasksCount(completed);
        enrollment.setTotalScore(score);
        enrollment.setProgressPercent(percent(completed, total));
        enrollment.setLastActivityAt(now);

        if (completed == 0) {
            enrollment.setStatus(ProgressStatus.NOT_STARTED);
        } else if (completed == total && total > 0) {
            enrollment.setStatus(ProgressStatus.COMPLETED);
            if (enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(now);
            }
            if (enrollment.getStartedAt() == null) {
                enrollment.setStartedAt(now);
            }
        } else {
            enrollment.setStatus(ProgressStatus.IN_PROGRESS);
            if (enrollment.getStartedAt() == null) {
                enrollment.setStartedAt(now);
            }
        }

        courseEnrollmentRepository.save(enrollment);
    }

    private BigDecimal percent(int completed, int total) {
        if (total <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(completed)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }
}
