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
import org.studyplatform.learningservice.CodeRunner.config.CodeExecutorServiceProperties;
import org.studyplatform.learningservice.CodeRunner.config.CourseServiceProperties;
import org.studyplatform.learningservice.common.ProgressStatus;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentCreateRequest;
import org.studyplatform.learningservice.courseenrollment.CourseEnrollmentUpdateRequest;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressCreateRequest;
import org.studyplatform.learningservice.moduleprogress.ModuleProgressUpdateRequest;
import org.studyplatform.learningservice.taskprogress.TaskProgressCreateRequest;
import org.studyplatform.learningservice.taskprogress.TaskProgressUpdateRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class LearningApiAdditionalContractsTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @ParameterizedTest
    @MethodSource("canonicalExecutionModeValues")
    void executionModeParsesCanonicalValues(String rawValue, ExecutionMode expectedMode) {
        assertThat(ExecutionMode.fromValue(rawValue)).isEqualTo(expectedMode);
    }

    static Stream<Arguments> canonicalExecutionModeValues() {
        return Stream.of(
                Arguments.of("BATCH", ExecutionMode.BATCH),
                Arguments.of("ONE_BY_ONE", ExecutionMode.ONE_BY_ONE),
                Arguments.of(" one_by_one ", ExecutionMode.ONE_BY_ONE)
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCourseEnrollmentUpdateRequests")
    void courseEnrollmentUpdateRequestValidationRejectsInvalidPayloads(
            CourseEnrollmentUpdateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidCourseEnrollmentUpdateRequests() {
        return Stream.of(
                Arguments.of(courseEnrollmentUpdate("100.01", 1, 3, 10), "progressPercent"),
                Arguments.of(courseEnrollmentUpdate("50.00", -1, 3, 10), "completedTasksCount"),
                Arguments.of(courseEnrollmentUpdate("50.00", 1, 3, -1), "totalScore")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidModuleProgressUpdateRequests")
    void moduleProgressUpdateRequestValidationRejectsInvalidPayloads(
            ModuleProgressUpdateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidModuleProgressUpdateRequests() {
        return Stream.of(
                Arguments.of(moduleProgressUpdate("-0.01", 1, 3, 10), "progressPercent"),
                Arguments.of(moduleProgressUpdate("50.00", 1, -1, 10), "totalTasksCount"),
                Arguments.of(moduleProgressUpdate("50.00", 1, 3, -1), "score")
        );
    }

    @ParameterizedTest
    @MethodSource("invalidTaskProgressUpdateRequests")
    void taskProgressUpdateRequestValidationRejectsInvalidPayloads(
            TaskProgressUpdateRequest request,
            String expectedPathFragment
    ) {
        assertViolates(request, expectedPathFragment);
    }

    static Stream<Arguments> invalidTaskProgressUpdateRequests() {
        return Stream.of(
                Arguments.of(taskProgressUpdate(-1, 10, 10), "attemptsCount"),
                Arguments.of(taskProgressUpdate(1, -1, 10), "bestScore"),
                Arguments.of(taskProgressUpdate(1, 10, -1), "lastScore")
        );
    }

    @Test
    void courseEnrollmentUpdateRequestStoresMutableProgressFields() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 6, 1, 10, 0);
        CourseEnrollmentUpdateRequest request = courseEnrollmentUpdate("75.50", 7, 10, 95);

        request.setStatus(ProgressStatus.IN_PROGRESS);
        request.setStartedAt(timestamp);
        request.setCompletedAt(timestamp.plusDays(1));
        request.setLastActivityAt(timestamp.plusHours(2));

        assertThat(request.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(request.getProgressPercent()).isEqualByComparingTo("75.50");
        assertThat(request.getCompletedTasksCount()).isEqualTo(7);
        assertThat(request.getTotalTasksCount()).isEqualTo(10);
        assertThat(request.getTotalScore()).isEqualTo(95);
        assertThat(request.getStartedAt()).isEqualTo(timestamp);
        assertThat(request.getCompletedAt()).isEqualTo(timestamp.plusDays(1));
        assertThat(request.getLastActivityAt()).isEqualTo(timestamp.plusHours(2));
    }

    @Test
    void moduleProgressUpdateRequestStoresMutableProgressFields() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 6, 1, 11, 0);
        ModuleProgressUpdateRequest request = moduleProgressUpdate("40.00", 2, 5, 30);

        request.setStatus(ProgressStatus.IN_PROGRESS);
        request.setStartedAt(timestamp);
        request.setCompletedAt(timestamp.plusDays(2));
        request.setLastActivityAt(timestamp.plusMinutes(30));

        assertThat(request.getStatus()).isEqualTo(ProgressStatus.IN_PROGRESS);
        assertThat(request.getProgressPercent()).isEqualByComparingTo("40.00");
        assertThat(request.getCompletedTasksCount()).isEqualTo(2);
        assertThat(request.getTotalTasksCount()).isEqualTo(5);
        assertThat(request.getScore()).isEqualTo(30);
        assertThat(request.getStartedAt()).isEqualTo(timestamp);
        assertThat(request.getCompletedAt()).isEqualTo(timestamp.plusDays(2));
        assertThat(request.getLastActivityAt()).isEqualTo(timestamp.plusMinutes(30));
    }

    @Test
    void taskProgressUpdateRequestStoresMutableAttemptFields() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 6, 1, 12, 0);
        TaskProgressUpdateRequest request = taskProgressUpdate(3, 80, 70);

        request.setStatus(ProgressStatus.COMPLETED);
        request.setIsCompleted(true);
        request.setFirstOpenedAt(timestamp.minusDays(1));
        request.setStartedAt(timestamp.minusHours(2));
        request.setFirstSuccessAt(timestamp.minusMinutes(20));
        request.setCompletedAt(timestamp);
        request.setLastSubmissionAt(timestamp.plusMinutes(5));
        request.setLastActivityAt(timestamp.plusMinutes(6));

        assertThat(request.getStatus()).isEqualTo(ProgressStatus.COMPLETED);
        assertThat(request.getAttemptsCount()).isEqualTo(3);
        assertThat(request.getBestScore()).isEqualTo(80);
        assertThat(request.getLastScore()).isEqualTo(70);
        assertThat(request.getIsCompleted()).isTrue();
        assertThat(request.getFirstOpenedAt()).isEqualTo(timestamp.minusDays(1));
        assertThat(request.getStartedAt()).isEqualTo(timestamp.minusHours(2));
        assertThat(request.getFirstSuccessAt()).isEqualTo(timestamp.minusMinutes(20));
        assertThat(request.getCompletedAt()).isEqualTo(timestamp);
        assertThat(request.getLastSubmissionAt()).isEqualTo(timestamp.plusMinutes(5));
        assertThat(request.getLastActivityAt()).isEqualTo(timestamp.plusMinutes(6));
    }

    @Test
    void validSubmissionCreateRequestHasNoValidationErrors() {
        SubmissionCreateRequest request = new SubmissionCreateRequest();
        request.setTaskId(1L);
        request.setLanguage("sql");
        request.setSourceCode("select 1");
        request.setExecutionMode(ExecutionMode.BATCH);

        assertThat(validatePaths(request)).isEmpty();
    }

    @Test
    void validCourseEnrollmentCreateRequestHasNoValidationErrors() {
        CourseEnrollmentCreateRequest request = courseEnrollment(1L, 2L, "100.00", 4, 4, 100);

        assertThat(validatePaths(request)).isEmpty();
    }

    @Test
    void validModuleProgressCreateRequestHasNoValidationErrors() {
        ModuleProgressCreateRequest request = moduleProgress(1L, 2L, 3L, "100.00", 2, 2, 50);

        assertThat(validatePaths(request)).isEmpty();
    }

    @Test
    void validTaskProgressCreateRequestHasNoValidationErrors() {
        TaskProgressCreateRequest request = taskProgress(1L, 2L, 3L, 4L, 1, 100, 100);

        assertThat(validatePaths(request)).isEmpty();
    }

    @Test
    void codeExecutorServicePropertiesUseDefaultsAndAllowOverrides() {
        CodeExecutorServiceProperties properties = new CodeExecutorServiceProperties();

        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8084");
        assertThat(properties.getAuthToken()).isEqualTo("dev-executor-service-token");

        properties.setBaseUrl("http://executor:8084");
        properties.setAuthToken("token");

        assertThat(properties.getBaseUrl()).isEqualTo("http://executor:8084");
        assertThat(properties.getAuthToken()).isEqualTo("token");
    }

    @Test
    void courseServicePropertiesUseDefaultsAndAllowOverrides() {
        CourseServiceProperties properties = new CourseServiceProperties();

        assertThat(properties.getBaseUrl()).isEqualTo("http://localhost:8082");
        assertThat(properties.getInternalApiKeyHeader()).isEqualTo("X-Internal-API-Key");
        assertThat(properties.getInternalApiKey()).isEqualTo("dev-course-service-internal-key");

        properties.setBaseUrl("http://course:8082");
        properties.setInternalApiKeyHeader("X-Test-Key");
        properties.setInternalApiKey("secret");

        assertThat(properties.getBaseUrl()).isEqualTo("http://course:8082");
        assertThat(properties.getInternalApiKeyHeader()).isEqualTo("X-Test-Key");
        assertThat(properties.getInternalApiKey()).isEqualTo("secret");
    }

    @Test
    void runItemRequestCanCarryOnlyQuizSelections() {
        RunItemRequest request = new RunItemRequest();

        request.setSelectedOptionIds(List.of(10L, 11L));

        assertThat(request.getSourceCode()).isNull();
        assertThat(request.getSql()).isNull();
        assertThat(request.getSelectedOptionIds()).containsExactly(10L, 11L);
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

    private static CourseEnrollmentUpdateRequest courseEnrollmentUpdate(
            String progressPercent,
            Integer completedTasksCount,
            Integer totalTasksCount,
            Integer totalScore
    ) {
        CourseEnrollmentUpdateRequest request = new CourseEnrollmentUpdateRequest();
        request.setProgressPercent(new BigDecimal(progressPercent));
        request.setCompletedTasksCount(completedTasksCount);
        request.setTotalTasksCount(totalTasksCount);
        request.setTotalScore(totalScore);
        return request;
    }

    private static ModuleProgressUpdateRequest moduleProgressUpdate(
            String progressPercent,
            Integer completedTasksCount,
            Integer totalTasksCount,
            Integer score
    ) {
        ModuleProgressUpdateRequest request = new ModuleProgressUpdateRequest();
        request.setProgressPercent(new BigDecimal(progressPercent));
        request.setCompletedTasksCount(completedTasksCount);
        request.setTotalTasksCount(totalTasksCount);
        request.setScore(score);
        return request;
    }

    private static TaskProgressUpdateRequest taskProgressUpdate(
            Integer attemptsCount,
            Integer bestScore,
            Integer lastScore
    ) {
        TaskProgressUpdateRequest request = new TaskProgressUpdateRequest();
        request.setAttemptsCount(attemptsCount);
        request.setBestScore(bestScore);
        request.setLastScore(lastScore);
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
