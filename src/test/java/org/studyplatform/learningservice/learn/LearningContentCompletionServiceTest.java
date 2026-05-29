package org.studyplatform.learningservice.learn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningContentCompletionServiceTest {

    @Mock
    private CourseItemContentClient courseItemContentClient;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private ModuleProgressRepository moduleProgressRepository;

    @Mock
    private TaskProgressRepository taskProgressRepository;

    @Test
    void completeContentItemCreatesTaskProgressAndRecalculatesProgress() {
        LearningContentCompletionService service = service();
        CourseEnrollment enrollment = enrollment(5L, 3L);
        AtomicReference<TaskProgress> savedTask = new AtomicReference<>();
        AtomicReference<ModuleProgress> savedModule = new AtomicReference<>();

        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseItemContentClient.getCourseItemContent(4L, "Bearer token"))
                .thenReturn(item(4L, 10L, 3L, "THEORY"));
        when(taskProgressRepository.findByUserIdAndCourseIdAndTaskId(5L, 3L, 4L)).thenReturn(Optional.empty());
        when(taskProgressRepository.save(any(TaskProgress.class))).thenAnswer(invocation -> {
            TaskProgress progress = invocation.getArgument(0);
            savedTask.set(progress);
            return progress;
        });
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 3L, 10L))
                .thenAnswer(invocation -> Optional.ofNullable(savedModule.get()));
        when(moduleProgressRepository.save(any(ModuleProgress.class))).thenAnswer(invocation -> {
            ModuleProgress progress = invocation.getArgument(0);
            savedModule.set(progress);
            return progress;
        });
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 3L, 10L))
                .thenAnswer(invocation -> List.of(savedTask.get()));
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 3L))
                .thenAnswer(invocation -> List.of(savedTask.get()));

        LearningItemCompletionResponse response = service.completeContentItem(5L, 3L, 4L, "Bearer token");

        assertThat(response.completed()).isTrue();
        assertThat(response.status()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(response.moduleId()).isEqualTo(10L);
        assertThat(savedTask.get().getIsCompleted()).isTrue();
        assertThat(savedTask.get().getCompletedAt()).isNotNull();
        assertThat(savedModule.get().getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(savedModule.get().getProgressPercent()).isEqualByComparingTo("100.00");
        assertThat(enrollment.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void completeContentItemAcceptsFileItems() {
        LearningContentCompletionService service = service();
        CourseEnrollment enrollment = enrollment(5L, 3L);
        TaskProgress task = task(5L, 3L, 10L, 4L);
        ModuleProgress module = moduleProgress(5L, 3L, 10L);

        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseItemContentClient.getCourseItemContent(4L, null)).thenReturn(item(4L, 10L, 3L, "FILE"));
        when(taskProgressRepository.findByUserIdAndCourseIdAndTaskId(5L, 3L, 4L)).thenReturn(Optional.of(task));
        when(taskProgressRepository.save(task)).thenReturn(task);
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 3L, 10L)).thenReturn(Optional.of(module));
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 3L, 10L)).thenReturn(List.of(task));
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 3L)).thenReturn(List.of(task));

        LearningItemCompletionResponse response = service.completeContentItem(5L, 3L, 4L, null);

        assertThat(response.completed()).isTrue();
        assertThat(task.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        verify(moduleProgressRepository).save(module);
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("100.00");
    }

    @Test
    void completeContentItemRejectsUsersWithoutEnrollment() {
        LearningContentCompletionService service = service();
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.completeContentItem(5L, 3L, 4L, null))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not enrolled in course");

        verifyNoInteractions(courseItemContentClient);
    }

    @Test
    void completeContentItemRejectsRunnableItems() {
        LearningContentCompletionService service = service();
        CourseEnrollment enrollment = enrollment(5L, 3L);
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseItemContentClient.getCourseItemContent(4L, null)).thenReturn(item(4L, 10L, 3L, "CODING"));

        assertThatThrownBy(() -> service.completeContentItem(5L, 3L, 4L, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Only THEORY and FILE items can be completed without executor");

        verify(taskProgressRepository, never()).save(any());
    }

    @Test
    void completeContentItemDoesNotOverwriteExistingCompletionTimestamp() {
        LearningContentCompletionService service = service();
        CourseEnrollment enrollment = enrollment(5L, 3L);
        LocalDateTime completedAt = LocalDateTime.now().minusDays(1);
        TaskProgress task = task(5L, 3L, 10L, 4L);
        task.setIsCompleted(true);
        task.setStatus(ProgressStatus.COMPLETED);
        task.setCompletedAt(completedAt);
        ModuleProgress module = moduleProgress(5L, 3L, 10L);

        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 3L)).thenReturn(Optional.of(enrollment));
        when(courseItemContentClient.getCourseItemContent(4L, null)).thenReturn(item(4L, 10L, 3L, "THEORY"));
        when(taskProgressRepository.findByUserIdAndCourseIdAndTaskId(5L, 3L, 4L)).thenReturn(Optional.of(task));
        when(taskProgressRepository.save(task)).thenReturn(task);
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 3L, 10L)).thenReturn(Optional.of(module));
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 3L, 10L)).thenReturn(List.of(task));
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 3L)).thenReturn(List.of(task));

        LearningItemCompletionResponse response = service.completeContentItem(5L, 3L, 4L, null);

        assertThat(response.completedAt()).isEqualTo(completedAt);
        assertThat(task.getCompletedAt()).isEqualTo(completedAt);
    }

    private LearningContentCompletionService service() {
        return new LearningContentCompletionService(
                courseItemContentClient,
                courseEnrollmentRepository,
                moduleProgressRepository,
                taskProgressRepository
        );
    }

    private CourseItemContentResponse item(Long itemId, Long moduleId, Long courseId, String itemType) {
        return new CourseItemContentResponse(itemId, moduleId, courseId, itemType, "Lesson");
    }

    private CourseEnrollment enrollment(Long userId, Long courseId) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setNickname("student");
        enrollment.setStatus(ProgressStatus.NOT_STARTED);
        enrollment.setProgressPercent(BigDecimal.ZERO);
        enrollment.setCompletedTasksCount(0);
        enrollment.setTotalTasksCount(0);
        enrollment.setTotalScore(0);
        return enrollment;
    }

    private TaskProgress task(Long userId, Long courseId, Long moduleId, Long itemId) {
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

    private ModuleProgress moduleProgress(Long userId, Long courseId, Long moduleId) {
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
}
