package org.studyplatform.learningservice.learn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningDeadlineServiceTest {

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private ModuleProgressRepository moduleProgressRepository;

    @Mock
    private TaskProgressRepository taskProgressRepository;

    @Test
    void getModuleDeadlineStateRejectsUnenrolledUser() {
        LearningDeadlineService service = service();
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getModuleDeadlineState(5L, 10L, 100L, LocalDateTime.now()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not enrolled in course");
    }

    @Test
    void getModuleDeadlineStateSplitsCompletedTasksBeforeAndAfterDeadline() {
        LearningDeadlineService service = service();
        LocalDateTime deadline = LocalDateTime.of(2026, 6, 1, 12, 0);
        ModuleProgress moduleProgress = moduleProgress(deadline.plusHours(1));
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(new CourseEnrollment()));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L))
                .thenReturn(Optional.of(moduleProgress));
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(List.of(
                completedTask(2L, deadline.plusMinutes(10)),
                completedTask(1L, deadline.minusMinutes(10))
        ));

        ModuleDeadlineStateResponse response = service.getModuleDeadlineState(5L, 10L, 100L, deadline);

        assertThat(response.deadlineStatus()).isEqualTo(DeadlineStatus.COMPLETED_LATE);
        assertThat(response.moduleCompletedBeforeDeadline()).isFalse();
        assertThat(response.tasksCompletedBeforeDeadline())
                .extracting(DeadlineTaskResponse::taskId)
                .containsExactly(1L);
        assertThat(response.tasksCompletedAfterDeadline())
                .extracting(DeadlineTaskResponse::taskId)
                .containsExactly(2L);
    }

    @Test
    void getModuleDeadlineStateTreatsCompletionAtDeadlineAsOnTime() {
        LearningDeadlineService service = service();
        LocalDateTime deadline = LocalDateTime.of(2026, 6, 1, 12, 0);
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(new CourseEnrollment()));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L))
                .thenReturn(Optional.of(moduleProgress(deadline)));
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(List.of());

        ModuleDeadlineStateResponse response = service.getModuleDeadlineState(5L, 10L, 100L, deadline);

        assertThat(response.deadlineStatus()).isEqualTo(DeadlineStatus.COMPLETED_ON_TIME);
        assertThat(response.moduleCompletedBeforeDeadline()).isTrue();
    }

    @Test
    void getModuleDeadlineStateReturnsOverdueWhenNotCompletedPastDeadline() {
        LearningDeadlineService service = service();
        LocalDateTime deadline = LocalDateTime.now().minusDays(1);
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(new CourseEnrollment()));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(Optional.empty());
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(List.of());

        ModuleDeadlineStateResponse response = service.getModuleDeadlineState(5L, 10L, 100L, deadline);

        assertThat(response.deadlineStatus()).isEqualTo(DeadlineStatus.OVERDUE);
        assertThat(response.moduleCompletedAt()).isNull();
        assertThat(response.moduleCompletedBeforeDeadline()).isNull();
    }

    @Test
    void getModuleDeadlineStateIgnoresIncompleteTasksAndUsesFirstSuccessFallback() {
        LearningDeadlineService service = service();
        LocalDateTime deadline = LocalDateTime.now().plusDays(1);
        TaskProgress completedWithoutCompletedAt = task(1L, ProgressStatus.COMPLETED, true);
        completedWithoutCompletedAt.setFirstSuccessAt(deadline.minusHours(1));
        TaskProgress incomplete = task(2L, ProgressStatus.IN_PROGRESS, false);
        incomplete.setFirstSuccessAt(deadline.minusHours(2));
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(new CourseEnrollment()));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(Optional.empty());
        when(taskProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L))
                .thenReturn(List.of(incomplete, completedWithoutCompletedAt));

        ModuleDeadlineStateResponse response = service.getModuleDeadlineState(5L, 10L, 100L, deadline);

        assertThat(response.deadlineStatus()).isEqualTo(DeadlineStatus.IN_PROGRESS_ON_TIME);
        assertThat(response.tasksCompletedBeforeDeadline())
                .extracting(DeadlineTaskResponse::taskId)
                .containsExactly(1L);
        assertThat(response.tasksCompletedAfterDeadline()).isEmpty();
    }

    private LearningDeadlineService service() {
        return new LearningDeadlineService(courseEnrollmentRepository, moduleProgressRepository, taskProgressRepository);
    }

    private ModuleProgress moduleProgress(LocalDateTime completedAt) {
        ModuleProgress progress = new ModuleProgress();
        progress.setCompletedAt(completedAt);
        return progress;
    }

    private TaskProgress completedTask(Long taskId, LocalDateTime completedAt) {
        TaskProgress task = task(taskId, ProgressStatus.COMPLETED, true);
        task.setCompletedAt(completedAt);
        return task;
    }

    private TaskProgress task(Long taskId, ProgressStatus status, boolean completed) {
        TaskProgress task = new TaskProgress();
        task.setTaskId(taskId);
        task.setStatus(status);
        task.setIsCompleted(completed);
        return task;
    }
}
