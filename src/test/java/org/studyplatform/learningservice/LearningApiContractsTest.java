package org.studyplatform.learningservice;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.studyplatform.learningservice.CodeRunner.api.ExecutionMode;
import org.studyplatform.learningservice.CodeRunner.api.RunItemRequest;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionCreateRequest;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionResultResponse;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionResultStatus;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionTestResultResponse;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentCreateRequest;
import org.studyplatform.learningservice.learn.CourseLeaderboardEntryResponse;
import org.studyplatform.learningservice.learn.CourseLeaderboardResponse;
import org.studyplatform.learningservice.learn.LeaderboardViewerRole;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressCreateRequest;
import org.studyplatform.learningservice.taskprogress.TaskProgressCreateRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LearningApiContractsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @MethodSource("executionModeAliases")
    void executionModeParsesSupportedValues(String rawValue, ExecutionMode expectedMode) {
        assertThat(ExecutionMode.fromValue(rawValue)).isEqualTo(expectedMode);
    }

    static Stream<Arguments> executionModeAliases() {
        return Stream.of(
                Arguments.of(null, ExecutionMode.BATCH),
                Arguments.of("", ExecutionMode.BATCH),
                Arguments.of(" batch ", ExecutionMode.BATCH),
                Arguments.of("one-by-one", ExecutionMode.ONE_BY_ONE),
                Arguments.of("ONEBYONE", ExecutionMode.ONE_BY_ONE)
        );
    }

    @Test
    void executionModeRejectsUnknownValue() {
        assertThatThrownBy(() -> ExecutionMode.fromValue("interactive"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void submissionCreateRequestDefaultsToBatchMode() {
        SubmissionCreateRequest request = new SubmissionCreateRequest();

        assertThat(request.getExecutionMode()).isEqualTo(ExecutionMode.BATCH);
    }

    @Test
    void runItemRequestStoresAllPayloadFields() {
        RunItemRequest request = new RunItemRequest();

        request.setSourceCode("print(1)");
        request.setSql("select 1");
        request.setSelectedOptionIds(List.of(1L, 3L));

        assertThat(request.getSourceCode()).isEqualTo("print(1)");
        assertThat(request.getSql()).isEqualTo("select 1");
        assertThat(request.getSelectedOptionIds()).containsExactly(1L, 3L);
    }

    @Test
    void responseRecordsExposeSubmissionAndLeaderboardValues() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 6, 1, 12, 0);
        SubmissionTestResultResponse testResult = new SubmissionTestResultResponse(
                "open-1", "OPEN", true, "42", null, 7L, 12.5
        );
        SubmissionResultResponse submission = new SubmissionResultResponse(
                10L,
                20L,
                SubmissionResultStatus.ACCEPTED,
                100.0,
                1,
                1,
                "42",
                "",
                List.of(testResult),
                createdAt
        );
        CourseLeaderboardEntryResponse currentUser = new CourseLeaderboardEntryResponse(
                5L, 1, "Ivan", new BigDecimal("100.00")
        );
        CourseLeaderboardResponse leaderboard = new CourseLeaderboardResponse(
                30L, LeaderboardViewerRole.LEARNER, List.of(currentUser), currentUser
        );

        assertThat(submission.status()).isEqualTo(SubmissionResultStatus.ACCEPTED);
        assertThat(submission.testResults()).containsExactly(testResult);
        assertThat(submission.createdAt()).isEqualTo(createdAt);
        assertThat(leaderboard.courseId()).isEqualTo(30L);
        assertThat(leaderboard.viewerRole()).isEqualTo(LeaderboardViewerRole.LEARNER);
        assertThat(leaderboard.currentUser()).isSameAs(currentUser);
    }

    @ParameterizedTest
    @MethodSource("invalidSubmissionCreateRequests")
    void submissionCreateRequestValidationRejectsInvalidPayloads(
            SubmissionCreateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidSubmissionCreateRequests() {
        return Stream.of(
                Arguments.of(submission(null, "sql", "select 1"), "taskId"),
                Arguments.of(submission(0L, "sql", "select 1"), "taskId"),
                Arguments.of(submission(1L, "", "select 1"), "language"),
                Arguments.of(submission(1L, "a".repeat(51), "select 1"), "language"),
                Arguments.of(submission(1L, "sql", ""), "sourceCode")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCourseEnrollmentRequests")
    void courseEnrollmentCreateRequestValidationRejectsInvalidPayloads(
            CourseEnrollmentCreateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidCourseEnrollmentRequests() {
        return Stream.of(
                Arguments.of(courseEnrollment(null, 2L, "50.00", 1, 3, 10), "userId"),
                Arguments.of(courseEnrollment(0L, 2L, "50.00", 1, 3, 10), "userId"),
                Arguments.of(courseEnrollment(1L, null, "50.00", 1, 3, 10), "courseId"),
                Arguments.of(courseEnrollment(1L, 0L, "50.00", 1, 3, 10), "courseId"),
                Arguments.of(courseEnrollment(1L, 2L, "100.01", 1, 3, 10), "progressPercent"),
                Arguments.of(courseEnrollment(1L, 2L, "50.00", -1, 3, 10), "completedTasksCount")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidModuleProgressRequests")
    void moduleProgressCreateRequestValidationRejectsInvalidPayloads(
            ModuleProgressCreateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidModuleProgressRequests() {
        return Stream.of(
                Arguments.of(moduleProgress(null, 2L, 3L, "50.00", 1, 3, 10), "userId"),
                Arguments.of(moduleProgress(1L, 0L, 3L, "50.00", 1, 3, 10), "courseId"),
                Arguments.of(moduleProgress(1L, 2L, null, "50.00", 1, 3, 10), "moduleId"),
                Arguments.of(moduleProgress(1L, 2L, 3L, "-0.01", 1, 3, 10), "progressPercent"),
                Arguments.of(moduleProgress(1L, 2L, 3L, "50.00", 1, 3, -1), "score")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidTaskProgressRequests")
    void taskProgressCreateRequestValidationRejectsInvalidPayloads(
            TaskProgressCreateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidTaskProgressRequests() {
        return Stream.of(
                Arguments.of(taskProgress(null, 2L, 3L, 4L, 1, 10, 10), "userId"),
                Arguments.of(taskProgress(1L, 0L, 3L, 4L, 1, 10, 10), "courseId"),
                Arguments.of(taskProgress(1L, 2L, null, 4L, 1, 10, 10), "moduleId"),
                Arguments.of(taskProgress(1L, 2L, 3L, 0L, 1, 10, 10), "taskId"),
                Arguments.of(taskProgress(1L, 2L, 3L, 4L, 1, -1, 10), "bestScore")
        );
    }

    private void assertViolates(Object value, String expectedPathFragment) {
        Set<String> paths = validatePaths(value);

        assertThat(paths).anySatisfy(path -> assertThat(path).contains(expectedPathFragment));
    }

    private Set<String> validatePaths(Object value) {
        return validator.validate(value)
                .stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private static SubmissionCreateRequest submission(Long taskId, String language, String sourceCode) {
        SubmissionCreateRequest request = new SubmissionCreateRequest();
        request.setTaskId(taskId);
        request.setLanguage(language);
        request.setSourceCode(sourceCode);
        return request;
    }

    private static CourseEnrollmentCreateRequest courseEnrollment(
            Long userId,
            Long courseId,
            String progressPercent,
            Integer completedTasksCount,
            Integer totalTasksCount,
            Integer totalScore
    ) {
        CourseEnrollmentCreateRequest request = new CourseEnrollmentCreateRequest();
        request.setUserId(userId);
        request.setCourseId(courseId);
        request.setProgressPercent(new BigDecimal(progressPercent));
        request.setCompletedTasksCount(completedTasksCount);
        request.setTotalTasksCount(totalTasksCount);
        request.setTotalScore(totalScore);
        return request;
    }

    private static ModuleProgressCreateRequest moduleProgress(
            Long userId,
            Long courseId,
            Long moduleId,
            String progressPercent,
            Integer completedTasksCount,
            Integer totalTasksCount,
            Integer score
    ) {
        ModuleProgressCreateRequest request = new ModuleProgressCreateRequest();
        request.setUserId(userId);
        request.setCourseId(courseId);
        request.setModuleId(moduleId);
        request.setProgressPercent(new BigDecimal(progressPercent));
        request.setCompletedTasksCount(completedTasksCount);
        request.setTotalTasksCount(totalTasksCount);
        request.setScore(score);
        return request;
    }

    private static TaskProgressCreateRequest taskProgress(
            Long userId,
            Long courseId,
            Long moduleId,
            Long taskId,
            Integer attemptsCount,
            Integer bestScore,
            Integer lastScore
    ) {
        TaskProgressCreateRequest request = new TaskProgressCreateRequest();
        request.setUserId(userId);
        request.setCourseId(courseId);
        request.setModuleId(moduleId);
        request.setTaskId(taskId);
        request.setAttemptsCount(attemptsCount);
        request.setBestScore(bestScore);
        request.setLastScore(lastScore);
        return request;
    }
}
