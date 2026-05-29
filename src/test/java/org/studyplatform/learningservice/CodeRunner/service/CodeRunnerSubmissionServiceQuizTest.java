package org.studyplatform.learningservice.CodeRunner.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;
import org.studyplatform.learningservice.CodeRunner.api.RunItemRequest;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionResultResponse;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionResultStatus;
import org.studyplatform.learningservice.CodeRunner.client.course.CourseExecutionPackageProvider;
import org.studyplatform.learningservice.CodeRunner.client.course.CourseItemExecutionPackage;
import org.studyplatform.learningservice.CodeRunner.client.course.QuizEvaluationPackage;
import org.studyplatform.learningservice.CodeRunner.client.course.QuizEvaluationPackageProvider;
import org.studyplatform.learningservice.CodeRunner.client.executor.CodeExecutorClient;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionTestResult;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionTestResultRepository;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionVerdict;
import org.studyplatform.learningservice.CodeRunner.persistence.TaskSubmission;
import org.studyplatform.learningservice.CodeRunner.persistence.TaskSubmissionRepository;
import org.studyplatform.learningservice.CodeRunner.persistence.TestResultStatus;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollment;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentRepository;
import org.studyplatform.learningservice.moduleprogress.ModuleProgress;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressRepository;
import org.studyplatform.learningservice.taskprogress.TaskProgress;
import org.studyplatform.learningservice.taskprogress.TaskProgressRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeRunnerSubmissionServiceQuizTest {

    @Mock
    private CourseExecutionPackageProvider courseExecutionPackageProvider;

    @Mock
    private QuizEvaluationPackageProvider quizEvaluationPackageProvider;

    @Mock
    private CodeExecutorClient codeExecutorClient;

    @Mock
    private TaskSubmissionRepository taskSubmissionRepository;

    @Mock
    private SubmissionTestResultRepository submissionTestResultRepository;

    @Mock
    private TaskProgressRepository taskProgressRepository;

    @Mock
    private ModuleProgressRepository moduleProgressRepository;

    @Mock
    private CourseEnrollmentRepository courseEnrollmentRepository;

    @Mock
    private EntityManager entityManager;

    private final AtomicReference<TaskProgress> savedTask = new AtomicReference<>();
    private final AtomicReference<TaskSubmission> savedSubmission = new AtomicReference<>();
    private final AtomicReference<SubmissionTestResult> savedResult = new AtomicReference<>();

    private void mockPersistenceSaves() {
        when(taskSubmissionRepository.save(any(TaskSubmission.class))).thenAnswer(invocation -> {
            TaskSubmission submission = invocation.getArgument(0);
            if (submission.getId() == null) {
                submission.setId(500L);
            }
            savedSubmission.set(submission);
            return submission;
        });
        when(submissionTestResultRepository.save(any(SubmissionTestResult.class))).thenAnswer(invocation -> {
            SubmissionTestResult result = invocation.getArgument(0);
            if (result.getId() == null) {
                result.setId(700L);
            }
            savedResult.set(result);
            return result;
        });
        when(taskProgressRepository.save(any(TaskProgress.class))).thenAnswer(invocation -> {
            TaskProgress progress = invocation.getArgument(0);
            savedTask.set(progress);
            return progress;
        });
        when(courseEnrollmentRepository.save(any(CourseEnrollment.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void submitQuizAcceptsExactCorrectSelectionAndCompletesTask() {
        CodeRunnerSubmissionService service = service();
        CourseEnrollment enrollment = enrollment();
        ModuleProgress module = moduleProgress();
        mockSuccessfulQuizDependencies(enrollment, module);

        RunItemRequest request = new RunItemRequest();
        request.setSelectedOptionIds(List.of(102L, 101L));

        SubmissionResultResponse response = service.submitItem(29L, 5L, 8L, "Bearer token", request);

        assertThat(response.status()).isEqualTo(SubmissionResultStatus.ACCEPTED);
        assertThat(response.score()).isEqualTo(100.0);
        assertThat(response.passedTests()).isEqualTo(1);
        assertThat(response.totalTests()).isEqualTo(1);
        assertThat(response.testResults()).hasSize(1);
        assertThat(response.testResults().getFirst().testKey()).isEqualTo("quiz-answer");
        assertThat(response.testResults().getFirst().passed()).isTrue();

        assertThat(savedSubmission.get().getLanguage()).isEqualTo("quiz");
        assertThat(savedSubmission.get().getSourceCode()).isEqualTo("[101,102]");
        assertThat(savedSubmission.get().getVerdict()).isEqualTo(SubmissionVerdict.OK);
        assertThat(savedResult.get().getStatus()).isEqualTo(TestResultStatus.PASSED);
        assertThat(savedResult.get().getInputSnapshot()).isEqualTo("[101,102]");
        assertThat(savedResult.get().getExpectedOutput()).isEqualTo("[101,102]");
        assertThat(savedTask.get().getIsCompleted()).isTrue();
        assertThat(savedTask.get().getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("100.00");
        assertThat(module.getProgressPercent()).isEqualByComparingTo("100.00");
        verifyNoInteractions(codeExecutorClient);
    }

    @Test
    void submitQuizStoresWrongAnswerWithoutCompletingTask() {
        CodeRunnerSubmissionService service = service();
        CourseEnrollment enrollment = enrollment();
        mockSuccessfulQuizDependencies(enrollment, moduleProgress());

        RunItemRequest request = new RunItemRequest();
        request.setSelectedOptionIds(List.of(101L));

        SubmissionResultResponse response = service.submitItem(29L, 5L, 8L, "Bearer token", request);

        assertThat(response.status()).isEqualTo(SubmissionResultStatus.WRONG_ANSWER);
        assertThat(response.score()).isEqualTo(0.0);
        assertThat(response.passedTests()).isZero();
        assertThat(response.totalTests()).isEqualTo(1);
        assertThat(savedSubmission.get().getVerdict()).isEqualTo(SubmissionVerdict.WA);
        assertThat(savedResult.get().getStatus()).isEqualTo(TestResultStatus.FAILED);
        assertThat(savedResult.get().getErrorOutput()).isEqualTo("Wrong answer");
        assertThat(savedTask.get().getIsCompleted()).isFalse();
        assertThat(savedTask.get().getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(enrollment.getProgressPercent()).isEqualByComparingTo("0.00");
        verifyNoInteractions(codeExecutorClient);
    }

    @Test
    void submitQuizRejectsOptionFromAnotherItem() {
        CodeRunnerSubmissionService service = service();
        CourseEnrollment enrollment = enrollment();

        when(courseEnrollmentRepository.findByUserIdAndCourseId(29L, 5L)).thenReturn(Optional.of(enrollment));
        when(courseExecutionPackageProvider.getExecutionPackage(8L, "Bearer token")).thenReturn(executionPackage());
        when(quizEvaluationPackageProvider.getQuizEvaluationPackage(8L, "Bearer token")).thenReturn(quizPackage());

        RunItemRequest request = new RunItemRequest();
        request.setSelectedOptionIds(List.of(999L));

        assertThatThrownBy(() -> service.submitItem(29L, 5L, 8L, "Bearer token", request))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Selected quiz option does not belong to item");

        verify(taskSubmissionRepository, never()).save(any());
        verifyNoInteractions(codeExecutorClient);
    }

    private void mockSuccessfulQuizDependencies(CourseEnrollment enrollment, ModuleProgress module) {
        mockPersistenceSaves();
        when(courseEnrollmentRepository.findByUserIdAndCourseId(29L, 5L)).thenReturn(Optional.of(enrollment));
        when(courseExecutionPackageProvider.getExecutionPackage(8L, "Bearer token")).thenReturn(executionPackage());
        when(quizEvaluationPackageProvider.getQuizEvaluationPackage(8L, "Bearer token")).thenReturn(quizPackage());
        when(taskProgressRepository.findByUserIdAndCourseIdAndTaskId(29L, 5L, 8L)).thenReturn(Optional.empty());
        when(taskSubmissionRepository.findTopByUserIdAndTaskIdOrderBySubmissionNumberDesc(29L, 8L)).thenReturn(Optional.empty());
        when(moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(29L, 5L, 2L)).thenReturn(Optional.of(module));

        Query lockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(eq(1), any())).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(1L);

        @SuppressWarnings("unchecked")
        TypedQuery<TaskProgress> taskProgressQuery = mock(TypedQuery.class);
        when(entityManager.createQuery(anyString(), eq(TaskProgress.class))).thenReturn(taskProgressQuery);
        when(taskProgressQuery.setParameter(anyString(), any())).thenReturn(taskProgressQuery);
        when(taskProgressQuery.getResultList()).thenAnswer(invocation -> List.of(savedTask.get()));
    }

    private CodeRunnerSubmissionService service() {
        return new CodeRunnerSubmissionService(
                courseExecutionPackageProvider,
                quizEvaluationPackageProvider,
                codeExecutorClient,
                taskSubmissionRepository,
                submissionTestResultRepository,
                taskProgressRepository,
                moduleProgressRepository,
                courseEnrollmentRepository,
                entityManager
        );
    }

    private CourseItemExecutionPackage executionPackage() {
        return new CourseItemExecutionPackage(
                8L,
                2L,
                5L,
                "QUIZ",
                "Syntax quiz",
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }

    private QuizEvaluationPackage quizPackage() {
        return new QuizEvaluationPackage(
                8L,
                2L,
                5L,
                "QUIZ",
                "Syntax quiz",
                List.of(
                        new QuizEvaluationPackage.QuizOption(101L, 0, "A", "First correct", true, null),
                        new QuizEvaluationPackage.QuizOption(102L, 1, "B", "Second correct", true, null),
                        new QuizEvaluationPackage.QuizOption(103L, 2, "C", "Wrong", false, null)
                )
        );
    }

    private CourseEnrollment enrollment() {
        CourseEnrollment enrollment = new CourseEnrollment();
        enrollment.setUserId(29L);
        enrollment.setCourseId(5L);
        enrollment.setNickname("student");
        enrollment.setStatus(ProgressStatus.NOT_STARTED);
        enrollment.setProgressPercent(BigDecimal.ZERO);
        enrollment.setCompletedTasksCount(0);
        enrollment.setTotalTasksCount(0);
        enrollment.setTotalScore(0);
        return enrollment;
    }

    private ModuleProgress moduleProgress() {
        ModuleProgress progress = new ModuleProgress();
        progress.setUserId(29L);
        progress.setCourseId(5L);
        progress.setModuleId(2L);
        progress.setStatus(ProgressStatus.NOT_STARTED);
        progress.setProgressPercent(BigDecimal.ZERO);
        progress.setCompletedTasksCount(0);
        progress.setTotalTasksCount(0);
        progress.setScore(0);
        return progress;
    }
}
