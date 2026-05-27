package org.studyplatform.learningservice.learn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningModuleStartServiceTest {

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private ModuleProgressRepository moduleProgressRepository;

    @Test
    void startModuleRejectsUnenrolledUser() {
        LearningModuleStartService service = service();
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.startModule(5L, 10L, 100L))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not enrolled in course");

        verify(moduleProgressRepository, never()).save(any());
    }

    @Test
    void startModuleCreatesProgressAndMarksEnrollmentOnFirstStart() {
        LearningModuleStartService service = service();
        CourseEnrollment enrollment = enrollment();
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(Optional.empty());
        when(moduleProgressRepository.save(any(ModuleProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ModuleStartResponse response = service.startModule(5L, 10L, 100L);

        assertThat(response.alreadyStarted()).isFalse();
        assertThat(response.startedAt()).isNotNull();
        assertThat(enrollment.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(enrollment.getStartedAt()).isEqualTo(response.startedAt());
        ArgumentCaptor<ModuleProgress> saved = ArgumentCaptor.forClass(ModuleProgress.class);
        verify(moduleProgressRepository).save(saved.capture());
        assertThat(saved.getValue().getCourseId()).isEqualTo(10L);
        assertThat(saved.getValue().getModuleId()).isEqualTo(100L);
    }

    @Test
    void startModuleMarksExistingNotStartedProgressAsInProgress() {
        LearningModuleStartService service = service();
        ModuleProgress progress = moduleProgress(null, ProgressStatus.NOT_STARTED);
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 10L)).thenReturn(Optional.of(enrollment()));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(Optional.of(progress));
        when(moduleProgressRepository.save(progress)).thenReturn(progress);

        ModuleStartResponse response = service.startModule(5L, 10L, 100L);

        assertThat(response.alreadyStarted()).isFalse();
        assertThat(progress.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(progress.getLastActivityAt()).isNotNull();
    }

    @Test
    void startModuleReturnsExistingStartedAtWithoutSavingProgressAgain() {
        LearningModuleStartService service = service();
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 27, 10, 0);
        CourseEnrollment enrollment = enrollment();
        enrollment.setStartedAt(startedAt);
        ModuleProgress progress = moduleProgress(startedAt, ProgressStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(Optional.of(progress));

        ModuleStartResponse response = service.startModule(5L, 10L, 100L);

        assertThat(response.alreadyStarted()).isTrue();
        assertThat(response.startedAt()).isEqualTo(startedAt);
        verify(moduleProgressRepository, never()).save(any());
    }

    @Test
    void startModuleBackfillsEnrollmentStartFromExistingModuleStart() {
        LearningModuleStartService service = service();
        LocalDateTime startedAt = LocalDateTime.of(2026, 5, 27, 10, 0);
        CourseEnrollment enrollment = enrollment();
        ModuleProgress progress = moduleProgress(startedAt, ProgressStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findByUserIdAndCourseIdForUpdate(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(5L, 10L, 100L)).thenReturn(Optional.of(progress));

        ModuleStartResponse response = service.startModule(5L, 10L, 100L);

        assertThat(response.alreadyStarted()).isTrue();
        assertThat(enrollment.getStartedAt()).isEqualTo(startedAt);
        assertThat(enrollment.getLastActivityAt()).isEqualTo(startedAt);
    }

    private LearningModuleStartService service() {
        return new LearningModuleStartService(courseEnrollmentRepository, moduleProgressRepository);
    }

    private CourseEnrollment enrollment() {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(5L);
        enrollment.setCourseId(10L);
        enrollment.setStatus(ProgressStatus.NOT_STARTED);
        return enrollment;
    }

    private ModuleProgress moduleProgress(LocalDateTime startedAt, ProgressStatus status) {
        ModuleProgress progress = new ModuleProgress();
        progress.setUserId(5L);
        progress.setCourseId(10L);
        progress.setModuleId(100L);
        progress.setStartedAt(startedAt);
        progress.setStatus(status);
        return progress;
    }
}
