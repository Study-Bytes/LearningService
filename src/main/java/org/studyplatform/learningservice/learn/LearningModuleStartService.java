package org.studyplatform.learningservice.learn;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Service
public class LearningModuleStartService {

    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final ModuleProgressRepository moduleProgressRepository;

    public LearningModuleStartService(
            CourseEnrollmentRepository courseEnrollmentRepository,
            ModuleProgressRepository moduleProgressRepository
    ) {
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.moduleProgressRepository = moduleProgressRepository;
    }

    @Transactional
    public ModuleStartResponse startModule(Long userId, Long courseId, Long moduleId) {
        CourseEnrollment enrollment = courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        ModuleProgress progress = moduleProgressRepository
                .findByUserIdAndCourseIdAndModuleId(userId, courseId, moduleId)
                .orElseGet(() -> createModuleProgress(userId, courseId, moduleId));

        boolean alreadyStarted = progress.getStartedAt() != null;
        if (!alreadyStarted) {
            LocalDateTime now = LocalDateTime.now();
            progress.setStartedAt(now);
            progress.setLastActivityAt(now);
            if (progress.getStatus() == null || progress.getStatus() == ProgressStatus.NOT_STARTED) {
                progress.setStatus(ProgressStatus.IN_PROGRESS);
            }
            markEnrollmentStarted(enrollment, now);
            progress = moduleProgressRepository.save(progress);
        } else if (enrollment.getStartedAt() == null) {
            markEnrollmentStarted(enrollment, progress.getStartedAt());
        }

        return new ModuleStartResponse(
                progress.getCourseId(),
                progress.getModuleId(),
                progress.getStartedAt(),
                alreadyStarted
        );
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

    private void markEnrollmentStarted(CourseEnrollment enrollment, LocalDateTime startedAt) {
        enrollment.setStartedAt(startedAt);
        enrollment.setLastActivityAt(startedAt);
        if (enrollment.getStatus() == ProgressStatus.NOT_STARTED) {
            enrollment.setStatus(ProgressStatus.IN_PROGRESS);
        }
    }
}
