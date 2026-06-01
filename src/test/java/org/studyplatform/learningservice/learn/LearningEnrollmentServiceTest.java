package org.studyplatform.learningservice.learn;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.common.exception.ConflictException;
import org.studyplatform.learningservice.common.exception.ForbiddenException;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LearningEnrollmentServiceTest {

    @Mock
    private CourseAvailabilityClient courseAvailabilityClient;

    @Mock
    private CourseOwnershipClient courseOwnershipClient;

    @Mock
    private CourseStructureClient courseStructureClient;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private TaskProgressRepository taskProgressRepository;

    @Test
    void enrollCreatesDefaultEnrollmentWhenCourseIsAvailable() {
        LearningEnrollmentService service = service();
        when(courseAvailabilityClient.getAvailability(10L))
                .thenReturn(new CourseAvailabilityResponse(10L, "PUBLISHED", "PUBLIC", true, true));
        when(courseEnrollmentRepository.existsByUserIdAndCourseId(5L, 10L)).thenReturn(false);
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        EnrollCourseResponse response = service.enroll(5L, 10L, " Student ");

        assertThat(response.courseId()).isEqualTo(10L);
        assertThat(response.status()).isEqualTo(ProgressStatus.NOT_STARTED);
        assertThat(response.progressPercent()).isZero();
        ArgumentCaptor<CourseEnrollment> enrollment = ArgumentCaptor.forClass(CourseEnrollment.class);
        verify(courseEnrollmentRepository).save(enrollment.capture());
        assertThat(enrollment.getValue().getUserId()).isEqualTo(5L);
        assertThat(enrollment.getValue().getNickname()).isEqualTo("Student");
    }

    @Test
    void enrollRejectsUnavailableCourse() {
        LearningEnrollmentService service = service();
        when(courseAvailabilityClient.getAvailability(10L))
                .thenReturn(new CourseAvailabilityResponse(10L, "DRAFT", "PUBLIC", true, false));

        assertThatThrownBy(() -> service.enroll(5L, 10L, "student"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Course is not available for enrollment");

        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void enrollRejectsDuplicateEnrollment() {
        LearningEnrollmentService service = service();
        when(courseAvailabilityClient.getAvailability(10L))
                .thenReturn(new CourseAvailabilityResponse(10L, "PUBLISHED", "PUBLIC", true, true));
        when(courseEnrollmentRepository.existsByUserIdAndCourseId(5L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.enroll(5L, 10L, "student"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("User is already enrolled in course");

        verify(courseEnrollmentRepository, never()).save(any());
    }

    @Test
    void getMyCoursesMapsEnrollmentRows() {
        LearningEnrollmentService service = service();
        CourseEnrollment first = enrollment(5L, 10L, "student", new BigDecimal("25.00"), ProgressStatus.IN_PROGRESS);
        CourseEnrollment second = enrollment(5L, 11L, "student", new BigDecimal("100.00"), ProgressStatus.COMPLETED);
        when(courseEnrollmentRepository.findByUserIdOrderByCreatedAtDesc(5L)).thenReturn(List.of(first, second));

        List<MyCourseEnrollmentResponse> response = service.getMyCourses(5L);

        assertThat(response)
                .extracting(MyCourseEnrollmentResponse::courseId)
                .containsExactly(10L, 11L);
        assertThat(response)
                .extracting(MyCourseEnrollmentResponse::progressPercent)
                .containsExactly(25, 100);
    }

    @Test
    void getCourseLeaderboardForLearnerKeepsCurrentUserAndSkipsOwnershipCheck() {
        LearningEnrollmentService service = service();
        CourseEnrollment enrollment = enrollment(5L, 10L, "student", new BigDecimal("40.00"), ProgressStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(courseStructureClient.getCourse(10L)).thenReturn(courseStructure());
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(List.of(
                task(101L, 100L, ProgressStatus.COMPLETED, true),
                task(102L, 100L, ProgressStatus.IN_PROGRESS, false),
                task(201L, 200L, ProgressStatus.NOT_STARTED, false)
        ));
        when(courseEnrollmentRepository.findTopLeaderboardRows(10L))
                .thenReturn(List.of(row(1L, "alice", "90.00")));
        when(courseEnrollmentRepository.findLeaderboardRowForUser(10L, 5L))
                .thenReturn(Optional.of(row(3L, "student", "40.00")));

        CourseLeaderboardResponse response = service.getCourseLeaderboard(5L, 10L, "student");

        assertThat(response.viewerRole()).isEqualTo(LeaderboardViewerRole.LEARNER);
        assertThat(response.top()).hasSize(1);
        assertThat(response.top().getFirst().userId()).isEqualTo(1L);
        assertThat(response.currentUser()).isNotNull();
        assertThat(response.currentUser().userId()).isEqualTo(5L);
        assertThat(response.currentUser().place()).isEqualTo(3);
        verifyNoInteractions(courseOwnershipClient);
    }

    @Test
    void getCourseLeaderboardForLearnerRefreshesNickname() {
        LearningEnrollmentService service = service();
        CourseEnrollment enrollment = enrollment(5L, 10L, "old", new BigDecimal("40.00"), ProgressStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(courseStructureClient.getCourse(10L)).thenReturn(courseStructure());
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(List.of(
                task(101L, 100L, ProgressStatus.COMPLETED, true),
                task(102L, 100L, ProgressStatus.IN_PROGRESS, false),
                task(201L, 200L, ProgressStatus.NOT_STARTED, false)
        ));
        when(courseEnrollmentRepository.findTopLeaderboardRows(10L)).thenReturn(List.of());
        when(courseEnrollmentRepository.findLeaderboardRowForUser(10L, 5L))
                .thenReturn(Optional.of(row(2L, "new", "40.00")));

        service.getCourseLeaderboard(5L, 10L, "new");

        assertThat(enrollment.getNickname()).isEqualTo("new");
        verify(courseEnrollmentRepository).saveAndFlush(enrollment);
    }

    @Test
    void getCourseLeaderboardForTeacherReturnsTopWithoutCurrentUser() {
        LearningEnrollmentService service = service();
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.empty());
        when(courseEnrollmentRepository.findTopLeaderboardRows(10L))
                .thenReturn(List.of(row(1L, "alice", "90.00")));
        when(courseOwnershipClient.isCourseOwner(10L, 5L)).thenReturn(true);

        CourseLeaderboardResponse response = service.getCourseLeaderboard(5L, 10L, "teacher");

        assertThat(response.viewerRole()).isEqualTo(LeaderboardViewerRole.TEACHER);
        assertThat(response.top()).hasSize(1);
        assertThat(response.currentUser()).isNull();
        verify(courseEnrollmentRepository, never()).findLeaderboardRowForUser(10L, 5L);
    }

    @Test
    void getCourseLeaderboardRejectsUserWhoIsNeitherLearnerNorTeacher() {
        LearningEnrollmentService service = service();
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.empty());
        when(courseEnrollmentRepository.findTopLeaderboardRows(10L)).thenReturn(List.of());
        when(courseOwnershipClient.isCourseOwner(10L, 5L)).thenReturn(false);

        assertThatThrownBy(() -> service.getCourseLeaderboard(5L, 10L, "stranger"))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("User is not enrolled in course");
    }

    @Test
    void getCourseStateOrdersItemsAndFindsNextIncompleteItem() {
        LearningEnrollmentService service = service();
        CourseEnrollment enrollment = enrollment(5L, 10L, "student", new BigDecimal("50.00"), ProgressStatus.IN_PROGRESS);
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(courseStructureClient.getCourse(10L)).thenReturn(courseStructure());
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(List.of(
                task(101L, 100L, ProgressStatus.COMPLETED, true),
                task(102L, 100L, ProgressStatus.LOCKED, false)
        ));
        when(taskProgressRepository.save(any(TaskProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningCourseStateResponse response = service.getCourseState(5L, 10L);

        assertThat(response.progressPercent()).isEqualTo(33);
        assertThat(response.nextItemId()).isEqualTo(102L);
        assertThat(response.items())
                .extracting(LearningCourseItemStateResponse::itemId)
                .containsExactly(101L, 102L, 201L);
        assertThat(response.items().get(0).completed()).isTrue();
        assertThat(response.items().get(1).locked()).isTrue();
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("33.33");
        assertThat(enrollment.getCompletedTasksCount()).isEqualTo(1);
        assertThat(enrollment.getTotalTasksCount()).isEqualTo(3);

        ArgumentCaptor<TaskProgress> saved = ArgumentCaptor.forClass(TaskProgress.class);
        verify(taskProgressRepository).save(saved.capture());
        assertThat(saved.getValue().getTaskId()).isEqualTo(201L);
        assertThat(saved.getValue().getModuleId()).isEqualTo(200L);
        assertThat(saved.getValue().getStatus()).isEqualTo(ProgressStatus.NOT_STARTED);
        assertThat(saved.getValue().getFirstOpenedAt()).isNull();
    }

    @Test
    void getCourseStateUsesAllCourseItemsAsProgressDenominator() {
        LearningEnrollmentService service = service();
        CourseEnrollment enrollment = enrollment(5L, 10L, "student", new BigDecimal("100.00"), ProgressStatus.COMPLETED);
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(Optional.of(enrollment));
        when(courseStructureClient.getCourse(10L)).thenReturn(courseStructure());
        when(taskProgressRepository.findByUserIdAndCourseId(5L, 10L)).thenReturn(List.of(
                task(101L, 100L, ProgressStatus.COMPLETED, true),
                task(102L, 100L, ProgressStatus.COMPLETED, true)
        ));
        when(taskProgressRepository.save(any(TaskProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningCourseStateResponse response = service.getCourseState(5L, 10L);

        assertThat(response.progressPercent()).isEqualTo(66);
        assertThat(response.enrollmentStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("66.67");
        assertThat(enrollment.getCompletedTasksCount()).isEqualTo(2);
        assertThat(enrollment.getTotalTasksCount()).isEqualTo(3);
    }

    @Test
    void getItemStateCreatesProgressAndNavigationForNewItem() {
        LearningEnrollmentService service = service();
        when(courseEnrollmentRepository.findByUserIdAndCourseId(5L, 10L))
                .thenReturn(Optional.of(enrollment(5L, 10L, "student", BigDecimal.ZERO, ProgressStatus.NOT_STARTED)));
        when(courseStructureClient.getCourse(10L)).thenReturn(courseStructure());
        when(taskProgressRepository.findByUserIdAndCourseIdAndTaskId(5L, 10L, 102L)).thenReturn(Optional.empty());
        when(taskProgressRepository.save(any(TaskProgress.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LearningItemStateResponse response = service.getItemState(5L, 10L, 102L);

        assertThat(response.navigation().previousItemId()).isEqualTo(101L);
        assertThat(response.navigation().nextItemId()).isEqualTo(201L);
        assertThat(response.progress().status()).isEqualTo(ProgressStatus.NOT_STARTED);
        ArgumentCaptor<TaskProgress> saved = ArgumentCaptor.forClass(TaskProgress.class);
        verify(taskProgressRepository).save(saved.capture());
        assertThat(saved.getValue().getModuleId()).isEqualTo(100L);
        assertThat(saved.getValue().getTaskId()).isEqualTo(102L);
    }

    private LearningEnrollmentService service() {
        return new LearningEnrollmentService(
                courseAvailabilityClient,
                courseOwnershipClient,
                courseStructureClient,
                courseEnrollmentRepository,
                taskProgressRepository
        );
    }

    private CourseStructureResponse courseStructure() {
        CourseStructureResponse.Item item101 = new CourseStructureResponse.Item(101L, 1);
        CourseStructureResponse.Item item102 = new CourseStructureResponse.Item(102L, 2);
        CourseStructureResponse.Item item201 = new CourseStructureResponse.Item(201L, 1);
        CourseStructureResponse.Module second = new CourseStructureResponse.Module(200L, 2, List.of(item201));
        CourseStructureResponse.Module first = new CourseStructureResponse.Module(100L, 1, List.of(item102, item101));
        return new CourseStructureResponse(10L, List.of(second, first));
    }

    private CourseEnrollment enrollment(
            Long userId,
            Long courseId,
            String nickname,
            BigDecimal progressPercent,
            ProgressStatus status
    ) {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(userId);
        enrollment.setCourseId(courseId);
        enrollment.setNickname(nickname);
        enrollment.setProgressPercent(progressPercent);
        enrollment.setStatus(status);
        return enrollment;
    }

    private TaskProgress task(Long taskId, Long moduleId, ProgressStatus status, boolean completed) {
        TaskProgress task = new TaskProgress();
        task.setTaskId(taskId);
        task.setModuleId(moduleId);
        task.setStatus(status);
        task.setIsCompleted(completed);
        return task;
    }

    private CourseEnrollmentRepository.CourseLeaderboardRow row(Long place, String nickname, String progressPercent) {
        return new CourseEnrollmentRepository.CourseLeaderboardRow() {
            @Override
            public Long getRankPlace() {
                return place;
            }

            @Override
            public Long getUserId() {
                return "student".equals(nickname) || "new".equals(nickname) ? 5L : place;
            }

            @Override
            public String getNickname() {
                return nickname;
            }

            @Override
            public BigDecimal getProgressPercent() {
                return new BigDecimal(progressPercent);
            }
        };
    }
}
