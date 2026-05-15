package org.studyplatform.learningservice.CodeRunner.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionCreateRequest;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionCreateResponse;
import org.studyplatform.learningservice.CodeRunner.api.ExecutionMode;
import org.studyplatform.learningservice.CodeRunner.client.course.CourseItemExecutionPackage;
import org.studyplatform.learningservice.CodeRunner.client.course.CourseExecutionPackageProvider;
import org.studyplatform.learningservice.CodeRunner.client.executor.CodeExecutorClient;
import org.studyplatform.learningservice.CodeRunner.client.executor.ExecutionCreateRequest;
import org.studyplatform.learningservice.CodeRunner.client.executor.ExecutionResponse;
import org.studyplatform.learningservice.CodeRunner.client.executor.ExecutionSessionCreateRequest;
import org.studyplatform.learningservice.CodeRunner.client.executor.ExecutionTestRunRequest;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionStatus;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionTestResult;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionTestResultRepository;
import org.studyplatform.learningservice.CodeRunner.persistence.SubmissionVerdict;
import org.studyplatform.learningservice.CodeRunner.persistence.TaskSubmission;
import org.studyplatform.learningservice.CodeRunner.persistence.TaskSubmissionRepository;
import org.studyplatform.learningservice.CodeRunner.persistence.TestResultStatus;
import org.studyplatform.learningservice.common.ProgressStatus;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

@Service
public class CodeRunnerSubmissionService {

    private final CourseExecutionPackageProvider courseExecutionPackageProvider;
    private final CodeExecutorClient codeExecutorClient;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;
    private final TaskProgressRepository taskProgressRepository;
    private final ModuleProgressRepository moduleProgressRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final EntityManager entityManager;

    public CodeRunnerSubmissionService(
            CourseExecutionPackageProvider courseExecutionPackageProvider,
            CodeExecutorClient codeExecutorClient,
            TaskSubmissionRepository taskSubmissionRepository,
            SubmissionTestResultRepository submissionTestResultRepository,
            TaskProgressRepository taskProgressRepository,
            ModuleProgressRepository moduleProgressRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            EntityManager entityManager
    ) {
        this.courseExecutionPackageProvider = courseExecutionPackageProvider;
        this.codeExecutorClient = codeExecutorClient;
        this.taskSubmissionRepository = taskSubmissionRepository;
        this.submissionTestResultRepository = submissionTestResultRepository;
        this.taskProgressRepository = taskProgressRepository;
        this.moduleProgressRepository = moduleProgressRepository;
        this.courseEnrollmentRepository = courseEnrollmentRepository;
        this.entityManager = entityManager;
    }

    @Transactional
    public SubmissionCreateResponse submit(
            Long userId,
            Long taskId,
            String authorizationHeader,
            @Valid SubmissionCreateRequest request
    ) {
        TaskProgress taskProgress = taskProgressRepository
                .findByUserIdAndTaskId(userId, taskId)
                .orElseThrow(() -> new NotFoundException("Task progress not found for user and task"));

        CourseItemExecutionPackage executionPackage =
                courseExecutionPackageProvider.getExecutionPackage(taskId, authorizationHeader);
        validateExecutionPackage(taskId, executionPackage);

        Long courseId = resolveAuthoritativeId(taskProgress.getCourseId(), executionPackage.courseId());
        Long moduleId = resolveAuthoritativeId(taskProgress.getModuleId(), executionPackage.moduleId());
        syncTaskProgressCourseContext(taskProgress, courseId, moduleId);

        int submissionNumber = nextSubmissionNumber(userId, taskId);

        TaskSubmission submission = new TaskSubmission();
        submission.setUserId(userId);
        submission.setCourseId(courseId);
        submission.setModuleId(moduleId);
        submission.setTaskId(taskId);
        submission.setSubmissionNumber(submissionNumber);
        submission.setLanguage(request.getLanguage());
        submission.setSourceCode(request.getSourceCode());
        submission.setStatus(SubmissionStatus.QUEUED);
        submission = taskSubmissionRepository.save(submission);

        LocalDateTime now = LocalDateTime.now();
        markTaskProgressOnSubmit(taskProgress, now);
        taskProgressRepository.save(taskProgress);

        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setStartedAt(now);
        submission = taskSubmissionRepository.save(submission);

        ExecutionCreateRequest executorRequest = buildExecutorRequest(executionPackage, request, submission);
        ExecutionMode executionMode = resolveExecutionMode(request.getExecutionMode());

        ExecutionResponse executionResponse;
        try {
            executionResponse = executeByMode(executionMode, executorRequest);
        } catch (RuntimeException ex) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setVerdict(SubmissionVerdict.PE);
            submission.setErrorMessage("Executor call failed");
            submission.setFinishedAt(LocalDateTime.now());
            taskSubmissionRepository.save(submission);
            return toResponse(submission);
        }

        submission.setExecutorRequestId(executionResponse.id());

        Map<String, ExecutionResponse.TestExecutionResult> executionResultByKey =
                indexExecutionResults(executionResponse.tests());

        List<SubmissionTestResult> savedResults = new ArrayList<>();
        int passedCount = 0;
        int totalCount = executionPackage.tests().size();
        boolean hasCompareFail = false;
        boolean hasRuntimeError = false;
        boolean hasTimeout = false;
        boolean hasMemoryLimit = false;
        boolean hasInternalError = false;

        for (CourseItemExecutionPackage.ExecutionTest test : executionPackage.tests()) {
            ExecutionResponse.TestExecutionResult testExecutionResult = executionResultByKey.get(test.testKey());

            SubmissionTestResult row = new SubmissionTestResult();
            row.setSubmissionId(submission.getId());
            row.setTestKey(test.testKey());
            row.setTestOrder(orZero(test.orderIndex()));
            row.setInputSnapshot(test.inputData());
            row.setExpectedOutput(test.expectedOutput());

            if (testExecutionResult == null) {
                row.setStatus(TestResultStatus.SKIPPED);
                row.setActualOutput(null);
                row.setErrorOutput(null);
            } else {
                row.setActualOutput(getOutputData(testExecutionResult.stdout()));
                row.setErrorOutput(getOutputData(testExecutionResult.stderr()));
                row.setExecutionTimeMs(testExecutionResult.durationMs());
                row.setMemoryKb(toKb(testExecutionResult.memoryMb()));

                String outcome = nullableUpper(testExecutionResult.outcome());
                if ("OK".equals(outcome)) {
                    boolean passed = compareOutputs(
                            test.expectedOutput(),
                            getOutputData(testExecutionResult.stdout()),
                            executionPackage.evaluationPolicy()
                    );
                    if (passed) {
                        row.setStatus(TestResultStatus.PASSED);
                        passedCount++;
                    } else {
                        row.setStatus(TestResultStatus.FAILED);
                        hasCompareFail = true;
                    }
                } else if ("RUNTIME_ERROR".equals(outcome)) {
                    row.setStatus(TestResultStatus.ERROR);
                    hasRuntimeError = true;
                } else if ("TIMEOUT".equals(outcome)) {
                    row.setStatus(TestResultStatus.ERROR);
                    hasTimeout = true;
                } else if ("MEMORY_LIMIT".equals(outcome)) {
                    row.setStatus(TestResultStatus.ERROR);
                    hasMemoryLimit = true;
                } else if ("INTERNAL_ERROR".equals(outcome)) {
                    row.setStatus(TestResultStatus.ERROR);
                    hasInternalError = true;
                } else {
                    row.setStatus(TestResultStatus.ERROR);
                    hasInternalError = true;
                }
            }

            savedResults.add(submissionTestResultRepository.save(row));
        }

        int score = totalCount == 0 ? 0 : (int) Math.round((passedCount * 100.0) / totalCount);
        boolean allPassed = totalCount > 0 && passedCount == totalCount;

        submission.setPassedTestsCount(passedCount);
        submission.setTotalTestsCount(totalCount);
        submission.setScore(score);
        submission.setVerdict(resolveVerdict(
                allPassed,
                hasCompareFail,
                hasRuntimeError,
                hasTimeout,
                hasMemoryLimit,
                hasInternalError
        ));
        submission.setStatus(SubmissionStatus.FINISHED);
        submission.setFinishedAt(LocalDateTime.now());
        submission = taskSubmissionRepository.save(submission);

        updateTaskProgressAfterExecution(taskProgress, score, allPassed);
        taskProgressRepository.save(taskProgress);

        recalculateModuleProgress(userId, courseId, moduleId);
        recalculateCourseProgress(userId, courseId);

        return toResponse(submission);
    }

    private ExecutionResponse executeByMode(ExecutionMode mode, ExecutionCreateRequest request) {
        if (mode == ExecutionMode.ONE_BY_ONE) {
            return executeOneByOne(request);
        }
        return codeExecutorClient.executeBatch(request);
    }

    private ExecutionResponse executeOneByOne(ExecutionCreateRequest request) {
        ExecutionSessionCreateRequest sessionRequest = new ExecutionSessionCreateRequest(
                request.language(),
                request.code(),
                request.limits(),
                request.executionPolicy(),
                request.metadata()
        );

        ExecutionResponse sessionResponse = codeExecutorClient.createSession(sessionRequest);
        String sessionId = sessionResponse == null ? null : sessionResponse.id();
        if (sessionId == null || sessionId.isBlank()) {
            throw new IllegalStateException("CodeExecutorService returned empty session id");
        }

        List<ExecutionResponse.TestExecutionResult> results = new ArrayList<>();
        long totalDurationMs = 0L;
        Integer peakMemoryMb = null;
        try {
            List<ExecutionCreateRequest.TestInput> tests = request.tests();
            if (tests != null) {
                for (ExecutionCreateRequest.TestInput test : tests) {
                    ExecutionTestRunRequest testRunRequest = new ExecutionTestRunRequest(
                            test.id(),
                            test.input(),
                            test.timeoutMs()
                    );
                    ExecutionResponse.TestExecutionResult result = codeExecutorClient.runTest(sessionId, testRunRequest);
                    if (result != null) {
                        results.add(result);

                        Integer durationMs = result.durationMs();
                        if (durationMs != null) {
                            totalDurationMs += durationMs.longValue();
                        }

                        Integer memoryMb = result.memoryMb();
                        if (memoryMb != null) {
                            peakMemoryMb = peakMemoryMb == null ? memoryMb : Math.max(peakMemoryMb, memoryMb);
                        }
                    }
                }
            }
        } finally {
            try {
                codeExecutorClient.cancelSession(sessionId);
            } catch (RuntimeException ignore) {
                // executor session cleanup error should not hide main execution result
            }
        }

        int boundedTotalDurationMs = totalDurationMs > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : (int) totalDurationMs;

        return new ExecutionResponse(
                sessionId,
                "FINISHED",
                request.language(),
                boundedTotalDurationMs,
                peakMemoryMb,
                results,
                request.metadata()
        );
    }

    private void validateExecutionPackage(
            Long taskId,
            CourseItemExecutionPackage executionPackage
    ) {
        if (executionPackage == null) {
            throw new IllegalStateException("CourseService returned empty execution package");
        }
        if (executionPackage.itemId() == null || !Objects.equals(executionPackage.itemId(), taskId)) {
            throw new IllegalStateException("CourseService returned mismatched itemId");
        }
        if (!"CODING".equalsIgnoreCase(nullSafe(executionPackage.itemType()))) {
            throw new IllegalStateException("Course item is not CODING");
        }
        if (executionPackage.tests() == null || executionPackage.tests().isEmpty()) {
            throw new IllegalStateException("Execution package has no tests");
        }
    }

    private Long resolveAuthoritativeId(Long currentId, Long courseServiceId) {
        return courseServiceId == null ? currentId : courseServiceId;
    }

    private void syncTaskProgressCourseContext(TaskProgress taskProgress, Long courseId, Long moduleId) {
        if (!Objects.equals(taskProgress.getCourseId(), courseId)) {
            taskProgress.setCourseId(courseId);
        }
        if (!Objects.equals(taskProgress.getModuleId(), moduleId)) {
            taskProgress.setModuleId(moduleId);
        }
    }

    private int nextSubmissionNumber(Long userId, Long taskId) {
        return taskSubmissionRepository
                .findTopByUserIdAndTaskIdOrderBySubmissionNumberDesc(userId, taskId)
                .map(s -> s.getSubmissionNumber() + 1)
                .orElse(1);
    }

    private void markTaskProgressOnSubmit(TaskProgress taskProgress, LocalDateTime now) {
        taskProgress.setAttemptsCount(taskProgress.getAttemptsCount() + 1);
        taskProgress.setLastSubmissionAt(now);
        taskProgress.setLastActivityAt(now);
        if (taskProgress.getStartedAt() == null) {
            taskProgress.setStartedAt(now);
        }
        if (taskProgress.getFirstOpenedAt() == null) {
            taskProgress.setFirstOpenedAt(now);
        }
        if (taskProgress.getStatus() == ProgressStatus.NOT_STARTED) {
            taskProgress.setStatus(ProgressStatus.IN_PROGRESS);
        }
    }

    private ExecutionCreateRequest buildExecutorRequest(
            CourseItemExecutionPackage executionPackage,
            SubmissionCreateRequest request,
            TaskSubmission submission
    ) {
        List<ExecutionCreateRequest.TestInput> tests = executionPackage.tests()
                .stream()
                .map(test -> new ExecutionCreateRequest.TestInput(
                        test.testKey(),
                        test.inputData(),
                        null
                ))
                .toList();

        CourseItemExecutionPackage.ExecutionLimits limits = executionPackage.limits();
        ExecutionCreateRequest.ExecutionLimits executorLimits = limits == null
                ? null
                : new ExecutionCreateRequest.ExecutionLimits(
                limits.timeLimitMs(),
                limits.memoryLimitMb(),
                limits.outputLimitKb()
        );

        CourseItemExecutionPackage.ExecutionPolicy policy = executionPackage.executionPolicy();
        ExecutionCreateRequest.ExecutionPolicy executorPolicy = policy == null
                ? null
                : new ExecutionCreateRequest.ExecutionPolicy(
                policy.networkDisabled(),
                policy.readOnlyFs()
        );

        Map<String, Object> metadata = new HashMap<>();
        metadata.put("taskId", String.valueOf(submission.getTaskId()));

        return new ExecutionCreateRequest(
                request.getLanguage(),
                request.getSourceCode(),
                tests,
                executorLimits,
                executorPolicy,
                metadata
        );
    }

    private ExecutionMode resolveExecutionMode(ExecutionMode mode) {
        return mode == null ? ExecutionMode.BATCH : mode;
    }

    private Map<String, ExecutionResponse.TestExecutionResult> indexExecutionResults(
            List<ExecutionResponse.TestExecutionResult> tests
    ) {
        if (tests == null || tests.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, ExecutionResponse.TestExecutionResult> map = new HashMap<>();
        for (ExecutionResponse.TestExecutionResult test : tests) {
            map.put(test.testId(), test);
        }
        return map;
    }

    private void updateTaskProgressAfterExecution(TaskProgress taskProgress, int score, boolean allPassed) {
        LocalDateTime now = LocalDateTime.now();
        taskProgress.setLastScore(score);
        taskProgress.setBestScore(Math.max(orZero(taskProgress.getBestScore()), score));
        taskProgress.setLastActivityAt(now);

        if (allPassed) {
            if (taskProgress.getFirstSuccessAt() == null) {
                taskProgress.setFirstSuccessAt(now);
            }
            taskProgress.setCompletedAt(now);
            taskProgress.setIsCompleted(true);
            taskProgress.setStatus(ProgressStatus.COMPLETED);
        } else if (!Boolean.TRUE.equals(taskProgress.getIsCompleted())) {
            taskProgress.setStatus(ProgressStatus.IN_PROGRESS);
        }
    }

    private void recalculateModuleProgress(Long userId, Long courseId, Long moduleId) {
        Optional<ModuleProgress> optional = moduleProgressRepository.findByUserIdAndCourseIdAndModuleId(
                userId,
                courseId,
                moduleId
        );
        if (optional.isEmpty()) {
            return;
        }

        List<TaskProgress> tasks = entityManager.createQuery(
                        "select tp from TaskProgress tp where tp.userId = :userId and tp.courseId = :courseId and tp.moduleId = :moduleId",
                        TaskProgress.class
                )
                .setParameter("userId", userId)
                .setParameter("courseId", courseId)
                .setParameter("moduleId", moduleId)
                .getResultList();

        int total = tasks.size();
        int completed = (int) tasks.stream().filter(tp -> Boolean.TRUE.equals(tp.getIsCompleted())).count();
        int score = tasks.stream().map(TaskProgress::getBestScore).mapToInt(this::orZero).sum();

        ModuleProgress moduleProgress = optional.get();
        moduleProgress.setTotalTasksCount(total);
        moduleProgress.setCompletedTasksCount(completed);
        moduleProgress.setScore(score);
        moduleProgress.setProgressPercent(percent(completed, total));
        moduleProgress.setLastActivityAt(LocalDateTime.now());

        if (completed == 0) {
            moduleProgress.setStatus(ProgressStatus.NOT_STARTED);
        } else if (completed == total && total > 0) {
            moduleProgress.setStatus(ProgressStatus.COMPLETED);
            if (moduleProgress.getCompletedAt() == null) {
                moduleProgress.setCompletedAt(LocalDateTime.now());
            }
        } else {
            moduleProgress.setStatus(ProgressStatus.IN_PROGRESS);
            if (moduleProgress.getStartedAt() == null) {
                moduleProgress.setStartedAt(LocalDateTime.now());
            }
        }

        moduleProgressRepository.save(moduleProgress);
    }

    private void recalculateCourseProgress(Long userId, Long courseId) {
        Optional<CourseEnrollment> optional = courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId);
        if (optional.isEmpty()) {
            return;
        }

        List<TaskProgress> tasks = entityManager.createQuery(
                        "select tp from TaskProgress tp where tp.userId = :userId and tp.courseId = :courseId",
                        TaskProgress.class
                )
                .setParameter("userId", userId)
                .setParameter("courseId", courseId)
                .getResultList();

        int total = tasks.size();
        int completed = (int) tasks.stream().filter(tp -> Boolean.TRUE.equals(tp.getIsCompleted())).count();
        int score = tasks.stream().map(TaskProgress::getBestScore).mapToInt(this::orZero).sum();

        CourseEnrollment enrollment = optional.get();
        enrollment.setTotalTasksCount(total);
        enrollment.setCompletedTasksCount(completed);
        enrollment.setTotalScore(score);
        enrollment.setProgressPercent(percent(completed, total));
        enrollment.setLastActivityAt(LocalDateTime.now());

        if (completed == 0) {
            enrollment.setStatus(ProgressStatus.NOT_STARTED);
        } else if (completed == total && total > 0) {
            enrollment.setStatus(ProgressStatus.COMPLETED);
            if (enrollment.getCompletedAt() == null) {
                enrollment.setCompletedAt(LocalDateTime.now());
            }
        } else {
            enrollment.setStatus(ProgressStatus.IN_PROGRESS);
            if (enrollment.getStartedAt() == null) {
                enrollment.setStartedAt(LocalDateTime.now());
            }
        }

        courseEnrollmentRepository.save(enrollment);
    }

    private SubmissionVerdict resolveVerdict(
            boolean allPassed,
            boolean hasCompareFail,
            boolean hasRuntimeError,
            boolean hasTimeout,
            boolean hasMemoryLimit,
            boolean hasInternalError
    ) {
        if (allPassed) {
            return SubmissionVerdict.OK;
        }
        if (hasInternalError) {
            return SubmissionVerdict.PE;
        }
        if (hasMemoryLimit) {
            return SubmissionVerdict.ML;
        }
        if (hasTimeout) {
            return SubmissionVerdict.TL;
        }
        if (hasRuntimeError) {
            return SubmissionVerdict.RE;
        }
        if (hasCompareFail) {
            return SubmissionVerdict.WA;
        }
        return SubmissionVerdict.PE;
    }

    private boolean compareOutputs(
            String expectedOutput,
            String actualOutput,
            CourseItemExecutionPackage.EvaluationPolicy policy
    ) {
        String expected = normalizeOutput(nullSafe(expectedOutput), policy);
        String actual = normalizeOutput(nullSafe(actualOutput), policy);
        return expected.equals(actual);
    }

    private String normalizeOutput(String value, CourseItemExecutionPackage.EvaluationPolicy policy) {
        String normalized = value;

        boolean normalizeLineEndings = policy != null && Boolean.TRUE.equals(policy.normalizeLineEndings());
        boolean trimTrailingWhitespaces = policy != null && Boolean.TRUE.equals(policy.trimTrailingWhitespaces());

        if (normalizeLineEndings) {
            normalized = normalized.replace("\r\n", "\n").replace("\r", "\n");
        }

        if (trimTrailingWhitespaces) {
            String[] lines = normalized.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                lines[i] = lines[i].replaceAll("[\\t ]+$", "");
            }
            normalized = String.join("\n", lines);
        }

        return normalized;
    }

    private String getOutputData(ExecutionResponse.OutputBlob blob) {
        return blob == null ? null : blob.data();
    }

    private Integer toKb(Integer memoryMb) {
        return memoryMb == null ? null : memoryMb * 1024;
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

    private String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private String nullableUpper(String value) {
        return value == null ? null : value.toUpperCase();
    }

    private SubmissionCreateResponse toResponse(TaskSubmission submission) {
        return new SubmissionCreateResponse(
                submission.getId(),
                submission.getStatus().name(),
                submission.getVerdict() == null ? null : submission.getVerdict().name(),
                submission.getScore(),
                submission.getPassedTestsCount(),
                submission.getTotalTestsCount(),
                submission.getExecutorRequestId()
        );
    }
}
