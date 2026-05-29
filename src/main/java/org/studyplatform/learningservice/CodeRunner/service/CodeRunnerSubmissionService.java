package org.studyplatform.learningservice.CodeRunner.service;

import jakarta.persistence.EntityManager;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionCreateRequest;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionCreateResponse;
import org.studyplatform.learningservice.CodeRunner.api.ExecutionMode;
import org.studyplatform.learningservice.CodeRunner.api.RunItemRequest;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionHistoryItemResponse;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionResultResponse;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionResultStatus;
import org.studyplatform.learningservice.CodeRunner.api.SubmissionTestResultResponse;
import org.studyplatform.learningservice.CodeRunner.client.course.CourseItemExecutionPackage;
import org.studyplatform.learningservice.CodeRunner.client.course.CourseExecutionPackageProvider;
import org.studyplatform.learningservice.CodeRunner.client.course.QuizEvaluationPackage;
import org.studyplatform.learningservice.CodeRunner.client.course.QuizEvaluationPackageProvider;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@Service
public class CodeRunnerSubmissionService {

    private static final Logger log = LoggerFactory.getLogger(CodeRunnerSubmissionService.class);

    private final CourseExecutionPackageProvider courseExecutionPackageProvider;
    private final QuizEvaluationPackageProvider quizEvaluationPackageProvider;
    private final CodeExecutorClient codeExecutorClient;
    private final TaskSubmissionRepository taskSubmissionRepository;
    private final SubmissionTestResultRepository submissionTestResultRepository;
    private final TaskProgressRepository taskProgressRepository;
    private final ModuleProgressRepository moduleProgressRepository;
    private final CourseEnrollmentRepository courseEnrollmentRepository;
    private final EntityManager entityManager;

    public CodeRunnerSubmissionService(
            CourseExecutionPackageProvider courseExecutionPackageProvider,
            QuizEvaluationPackageProvider quizEvaluationPackageProvider,
            CodeExecutorClient codeExecutorClient,
            TaskSubmissionRepository taskSubmissionRepository,
            SubmissionTestResultRepository submissionTestResultRepository,
            TaskProgressRepository taskProgressRepository,
            ModuleProgressRepository moduleProgressRepository,
            CourseEnrollmentRepository courseEnrollmentRepository,
            EntityManager entityManager
    ) {
        this.courseExecutionPackageProvider = courseExecutionPackageProvider;
        this.quizEvaluationPackageProvider = quizEvaluationPackageProvider;
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

        int submissionNumber = reserveNextSubmissionNumber(userId, taskId);

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
        boolean hasCompilationError = false;
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
                } else if ("COMPILATION_ERROR".equals(outcome) || "COMPILE_ERROR".equals(outcome)) {
                    row.setStatus(TestResultStatus.ERROR);
                    hasCompilationError = true;
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
                hasCompilationError,
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

    @Transactional
    public SubmissionResultResponse runItem(
            Long userId,
            Long courseId,
            Long itemId,
            String authorizationHeader,
            @Valid RunItemRequest request
    ) {
        log.info(
                "Run item requested userId={} courseId={} itemId={} sourceCodeLength={} sqlLength={} selectedOptions={}",
                userId,
                courseId,
                itemId,
                textLength(request.getSourceCode()),
                textLength(request.getSql()),
                listSize(request.getSelectedOptionIds())
        );

        CourseEnrollment enrollment = courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        CourseItemExecutionPackage executionPackage =
                courseExecutionPackageProvider.getExecutionPackage(itemId, authorizationHeader);
        validateRunnableExecutionPackage(courseId, itemId, executionPackage);

        List<CourseItemExecutionPackage.ExecutionTest> tests = openTests(executionPackage);
        if (tests.isEmpty()) {
            throw unprocessable("Course item has no open tests to run");
        }

        String code = resolveRunCode(executionPackage, request);
        String language = resolveRunLanguage(executionPackage);
        Long moduleId = executionPackage.moduleId();

        lockUserTaskSubmissionSequence(userId, itemId);

        TaskProgress taskProgress = taskProgressRepository.findByUserIdAndCourseIdAndTaskId(userId, courseId, itemId)
                .orElseGet(() -> createTaskProgress(userId, courseId, moduleId, itemId));
        syncTaskProgressCourseContext(taskProgress, courseId, moduleId);

        int submissionNumber = nextSubmissionNumber(userId, itemId);
        TaskSubmission submission = new TaskSubmission();
        submission.setUserId(userId);
        submission.setCourseId(courseId);
        submission.setModuleId(moduleId);
        submission.setTaskId(itemId);
        submission.setSubmissionNumber(submissionNumber);
        submission.setLanguage(language);
        submission.setSourceCode(code);
        submission.setStatus(SubmissionStatus.QUEUED);
        submission = taskSubmissionRepository.save(submission);

        LocalDateTime now = LocalDateTime.now();
        markEnrollmentOnActivity(enrollment, now);
        markTaskProgressOnRun(taskProgress, now);
        courseEnrollmentRepository.save(enrollment);
        taskProgressRepository.save(taskProgress);

        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setStartedAt(now);
        submission = taskSubmissionRepository.save(submission);

        ExecutionCreateRequest executorRequest = buildExecutorRequest(executionPackage, tests, language, code, submission);

        ExecutionResponse executionResponse;
        try {
            executionResponse = executeByMode(ExecutionMode.BATCH, executorRequest);
        } catch (RuntimeException ex) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setVerdict(SubmissionVerdict.PE);
            submission.setTotalTestsCount(tests.size());
            submission.setErrorMessage("Executor call failed");
            submission.setFinishedAt(LocalDateTime.now());
            submission = taskSubmissionRepository.save(submission);
            return toSubmissionResultResponse(submission, List.of(), visibilityByTestKey(tests));
        }

        submission.setExecutorRequestId(executionResponse.id());
        ExecutionOutcome outcome = persistExecutionResults(submission, executionPackage, tests, executionResponse);

        int score = outcome.totalCount() == 0 ? 0 : (int) Math.round((outcome.passedCount() * 100.0) / outcome.totalCount());
        boolean allPassed = outcome.totalCount() > 0 && outcome.passedCount() == outcome.totalCount();

        submission.setPassedTestsCount(outcome.passedCount());
        submission.setTotalTestsCount(outcome.totalCount());
        submission.setScore(score);
        submission.setVerdict(resolveVerdict(
                allPassed,
                outcome.hasCompareFail(),
                outcome.hasCompilationError(),
                outcome.hasRuntimeError(),
                outcome.hasTimeout(),
                outcome.hasMemoryLimit(),
                outcome.hasInternalError()
        ));
        submission.setStatus(SubmissionStatus.FINISHED);
        submission.setFinishedAt(LocalDateTime.now());
        submission = taskSubmissionRepository.save(submission);

        updateTaskProgressAfterRun(taskProgress, score);
        taskProgressRepository.save(taskProgress);

        log.info(
                "Run item finished userId={} courseId={} itemId={} submissionId={} status={} score={} passedTests={} totalTests={}",
                userId,
                courseId,
                itemId,
                submission.getId(),
                toSubmissionResultStatus(submission),
                submission.getScore(),
                submission.getPassedTestsCount(),
                submission.getTotalTestsCount()
        );

        return toSubmissionResultResponse(submission, outcome.results(), visibilityByTestKey(tests));
    }

    @Transactional
    public SubmissionResultResponse submitItem(
            Long userId,
            Long courseId,
            Long itemId,
            String authorizationHeader,
            @Valid RunItemRequest request
    ) {
        log.info(
                "Submit item requested userId={} courseId={} itemId={} sourceCodeLength={} sqlLength={} selectedOptions={}",
                userId,
                courseId,
                itemId,
                textLength(request.getSourceCode()),
                textLength(request.getSql()),
                listSize(request.getSelectedOptionIds())
        );

        CourseEnrollment enrollment = courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        CourseItemExecutionPackage executionPackage =
                courseExecutionPackageProvider.getExecutionPackage(itemId, authorizationHeader);
        validateCourseItemContext(courseId, itemId, executionPackage);
        if ("QUIZ".equals(nullableUpper(executionPackage.itemType()))) {
            return submitQuizItem(userId, courseId, itemId, authorizationHeader, request, enrollment, executionPackage);
        }
        validateRunnableExecutionPackage(courseId, itemId, executionPackage);

        List<CourseItemExecutionPackage.ExecutionTest> tests = executionPackage.tests();
        String code = resolveRunCode(executionPackage, request);
        String language = resolveRunLanguage(executionPackage);
        Long moduleId = executionPackage.moduleId();

        lockUserTaskSubmissionSequence(userId, itemId);

        TaskProgress taskProgress = taskProgressRepository.findByUserIdAndCourseIdAndTaskId(userId, courseId, itemId)
                .orElseGet(() -> createTaskProgress(userId, courseId, moduleId, itemId));
        syncTaskProgressCourseContext(taskProgress, courseId, moduleId);

        int submissionNumber = nextSubmissionNumber(userId, itemId);
        TaskSubmission submission = new TaskSubmission();
        submission.setUserId(userId);
        submission.setCourseId(courseId);
        submission.setModuleId(moduleId);
        submission.setTaskId(itemId);
        submission.setSubmissionNumber(submissionNumber);
        submission.setLanguage(language);
        submission.setSourceCode(code);
        submission.setStatus(SubmissionStatus.QUEUED);
        submission = taskSubmissionRepository.save(submission);

        LocalDateTime now = LocalDateTime.now();
        markEnrollmentOnActivity(enrollment, now);
        markTaskProgressOnSubmit(taskProgress, now);
        courseEnrollmentRepository.save(enrollment);
        taskProgressRepository.save(taskProgress);

        submission.setStatus(SubmissionStatus.RUNNING);
        submission.setStartedAt(now);
        submission = taskSubmissionRepository.save(submission);

        ExecutionCreateRequest executorRequest = buildExecutorRequest(executionPackage, tests, language, code, submission);

        ExecutionResponse executionResponse;
        try {
            executionResponse = executeByMode(ExecutionMode.BATCH, executorRequest);
        } catch (RuntimeException ex) {
            submission.setStatus(SubmissionStatus.FAILED);
            submission.setVerdict(SubmissionVerdict.PE);
            submission.setTotalTestsCount(tests.size());
            submission.setErrorMessage("Executor call failed");
            submission.setFinishedAt(LocalDateTime.now());
            submission = taskSubmissionRepository.save(submission);
            return toSubmissionResultResponse(submission, List.of(), visibilityByTestKey(tests));
        }

        submission.setExecutorRequestId(executionResponse.id());
        ExecutionOutcome outcome = persistExecutionResults(submission, executionPackage, tests, executionResponse);

        int score = outcome.totalCount() == 0 ? 0 : (int) Math.round((outcome.passedCount() * 100.0) / outcome.totalCount());
        boolean allPassed = outcome.totalCount() > 0 && outcome.passedCount() == outcome.totalCount();

        submission.setPassedTestsCount(outcome.passedCount());
        submission.setTotalTestsCount(outcome.totalCount());
        submission.setScore(score);
        submission.setVerdict(resolveVerdict(
                allPassed,
                outcome.hasCompareFail(),
                outcome.hasCompilationError(),
                outcome.hasRuntimeError(),
                outcome.hasTimeout(),
                outcome.hasMemoryLimit(),
                outcome.hasInternalError()
        ));
        submission.setStatus(SubmissionStatus.FINISHED);
        submission.setFinishedAt(LocalDateTime.now());
        submission = taskSubmissionRepository.save(submission);

        updateTaskProgressAfterExecution(taskProgress, score, allPassed);
        taskProgressRepository.save(taskProgress);

        recalculateModuleProgress(userId, courseId, moduleId);
        recalculateCourseProgress(userId, courseId);

        log.info(
                "Submit item finished userId={} courseId={} itemId={} submissionId={} status={} score={} passedTests={} totalTests={}",
                userId,
                courseId,
                itemId,
                submission.getId(),
                toSubmissionResultStatus(submission),
                submission.getScore(),
                submission.getPassedTestsCount(),
                submission.getTotalTestsCount()
        );

        return toSubmissionResultResponse(submission, outcome.results(), visibilityByTestKey(tests));
    }

    private SubmissionResultResponse submitQuizItem(
            Long userId,
            Long courseId,
            Long itemId,
            String authorizationHeader,
            RunItemRequest request,
            CourseEnrollment enrollment,
            CourseItemExecutionPackage executionPackage
    ) {
        if (executionPackage.moduleId() == null) {
            throw new IllegalStateException("CourseService returned empty moduleId");
        }

        QuizEvaluationPackage quizPackage = quizEvaluationPackageProvider.getQuizEvaluationPackage(itemId, authorizationHeader);
        validateQuizEvaluationPackage(courseId, itemId, executionPackage.moduleId(), quizPackage);

        List<Long> selectedOptionIds = normalizeOptionIds(request.getSelectedOptionIds());
        if (selectedOptionIds.isEmpty()) {
            throw unprocessable("At least one quiz option must be selected");
        }

        Set<Long> validOptionIds = quizPackage.options().stream()
                .map(QuizEvaluationPackage.QuizOption::id)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new));
        List<Long> correctOptionIds = quizPackage.options().stream()
                .filter(option -> Boolean.TRUE.equals(option.correct()))
                .map(QuizEvaluationPackage.QuizOption::id)
                .filter(Objects::nonNull)
                .toList();
        if (correctOptionIds.isEmpty()) {
            throw unprocessable("Quiz has no correct options");
        }

        for (Long selectedOptionId : selectedOptionIds) {
            if (!validOptionIds.contains(selectedOptionId)) {
                throw unprocessable("Selected quiz option does not belong to item");
            }
        }

        boolean allPassed = sameIds(selectedOptionIds, correctOptionIds);
        int score = allPassed ? 100 : 0;
        int passedCount = allPassed ? 1 : 0;
        Long moduleId = executionPackage.moduleId();

        lockUserTaskSubmissionSequence(userId, itemId);

        TaskProgress taskProgress = taskProgressRepository.findByUserIdAndCourseIdAndTaskId(userId, courseId, itemId)
                .orElseGet(() -> createTaskProgress(userId, courseId, moduleId, itemId));
        syncTaskProgressCourseContext(taskProgress, courseId, moduleId);

        int submissionNumber = nextSubmissionNumber(userId, itemId);
        TaskSubmission submission = new TaskSubmission();
        submission.setUserId(userId);
        submission.setCourseId(courseId);
        submission.setModuleId(moduleId);
        submission.setTaskId(itemId);
        submission.setSubmissionNumber(submissionNumber);
        submission.setLanguage("quiz");
        submission.setSourceCode(optionIdsSnapshot(selectedOptionIds));
        submission.setStatus(SubmissionStatus.QUEUED);
        submission = taskSubmissionRepository.save(submission);

        LocalDateTime now = LocalDateTime.now();
        markEnrollmentOnActivity(enrollment, now);
        markTaskProgressOnSubmit(taskProgress, now);
        courseEnrollmentRepository.save(enrollment);
        taskProgressRepository.save(taskProgress);

        submission.setStatus(SubmissionStatus.FINISHED);
        submission.setStartedAt(now);
        submission.setFinishedAt(now);
        submission.setPassedTestsCount(passedCount);
        submission.setTotalTestsCount(1);
        submission.setScore(score);
        submission.setVerdict(allPassed ? SubmissionVerdict.OK : SubmissionVerdict.WA);
        submission = taskSubmissionRepository.save(submission);

        SubmissionTestResult result = new SubmissionTestResult();
        result.setSubmissionId(submission.getId());
        result.setTestKey("quiz-answer");
        result.setTestOrder(0);
        result.setStatus(allPassed ? TestResultStatus.PASSED : TestResultStatus.FAILED);
        result.setInputSnapshot(optionIdsSnapshot(selectedOptionIds));
        result.setExpectedOutput(optionIdsSnapshot(correctOptionIds));
        result.setErrorOutput(allPassed ? null : "Wrong answer");
        result = submissionTestResultRepository.save(result);

        updateTaskProgressAfterExecution(taskProgress, score, allPassed);
        taskProgressRepository.save(taskProgress);

        recalculateModuleProgress(userId, courseId, moduleId);
        recalculateCourseProgress(userId, courseId);

        log.info(
                "Submit quiz finished userId={} courseId={} itemId={} submissionId={} status={} score={} selectedOptions={}",
                userId,
                courseId,
                itemId,
                submission.getId(),
                toSubmissionResultStatus(submission),
                submission.getScore(),
                selectedOptionIds.size()
        );

        return toSubmissionResultResponse(submission, List.of(result), Map.of("quiz-answer", "OPEN"));
    }

    @Transactional(readOnly = true)
    public List<SubmissionHistoryItemResponse> getItemSubmissions(
            Long userId,
            Long courseId,
            Long itemId,
            String authorizationHeader
    ) {
        log.info("Submission history requested userId={} courseId={} itemId={}", userId, courseId, itemId);

        courseEnrollmentRepository.findByUserIdAndCourseId(userId, courseId)
                .orElseThrow(() -> new ForbiddenException("User is not enrolled in course"));

        CourseItemExecutionPackage executionPackage =
                courseExecutionPackageProvider.getExecutionPackage(itemId, authorizationHeader);
        validateCourseItemContext(courseId, itemId, executionPackage);

        List<SubmissionHistoryItemResponse> history = taskSubmissionRepository.findByUserIdAndCourseIdAndTaskIdOrderByCreatedAtDesc(userId, courseId, itemId)
                .stream()
                .map(this::toSubmissionHistoryItemResponse)
                .toList();
        log.info("Submission history returned userId={} courseId={} itemId={} count={}", userId, courseId, itemId, history.size());
        return history;
    }

    @Transactional(readOnly = true)
    public SubmissionResultResponse getSubmission(
            Long userId,
            Long submissionId,
            String authorizationHeader
    ) {
        log.info("Submission result requested userId={} submissionId={}", userId, submissionId);

        TaskSubmission submission = taskSubmissionRepository.findById(submissionId)
                .orElseThrow(() -> new NotFoundException("Submission not found: " + submissionId));

        if (!Objects.equals(submission.getUserId(), userId)) {
            throw new ForbiddenException("Submission does not belong to current user");
        }

        List<SubmissionTestResult> results = submissionTestResultRepository
                .findBySubmissionIdOrderByTestOrderAsc(submissionId);

        CourseItemExecutionPackage executionPackage =
                courseExecutionPackageProvider.getExecutionPackage(submission.getTaskId(), authorizationHeader);

        SubmissionResultResponse response = toSubmissionResultResponse(
                submission,
                results,
                visibilityByTestKey(executionPackage)
        );
        log.info(
                "Submission result returned userId={} submissionId={} itemId={} status={} tests={}",
                userId,
                submissionId,
                submission.getTaskId(),
                response.status(),
                response.testResults() == null ? 0 : response.testResults().size()
        );
        return response;
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

    private void validateCourseItemContext(
            Long courseId,
            Long itemId,
            CourseItemExecutionPackage executionPackage
    ) {
        if (executionPackage == null) {
            throw new IllegalStateException("CourseService returned empty execution package");
        }
        if (executionPackage.itemId() == null || !Objects.equals(executionPackage.itemId(), itemId)) {
            throw new NotFoundException("Course item not found: " + itemId);
        }
        if (executionPackage.courseId() == null || !Objects.equals(executionPackage.courseId(), courseId)) {
            throw new NotFoundException("Course item not found in course");
        }
    }

    private void validateQuizEvaluationPackage(
            Long courseId,
            Long itemId,
            Long moduleId,
            QuizEvaluationPackage quizPackage
    ) {
        if (quizPackage == null) {
            throw new IllegalStateException("CourseService returned empty quiz evaluation package");
        }
        if (quizPackage.itemId() == null || !Objects.equals(quizPackage.itemId(), itemId)) {
            throw new NotFoundException("Course item not found: " + itemId);
        }
        if (quizPackage.courseId() == null || !Objects.equals(quizPackage.courseId(), courseId)) {
            throw new NotFoundException("Course item not found in course");
        }
        if (quizPackage.moduleId() == null || !Objects.equals(quizPackage.moduleId(), moduleId)) {
            throw new IllegalStateException("CourseService returned mismatched quiz moduleId");
        }
        if (!"QUIZ".equals(nullableUpper(quizPackage.itemType()))) {
            throw unprocessable("Item type is not QUIZ");
        }
        if (quizPackage.options() == null || quizPackage.options().isEmpty()) {
            throw unprocessable("Quiz has no options");
        }
    }

    private void validateRunnableExecutionPackage(
            Long courseId,
            Long itemId,
            CourseItemExecutionPackage executionPackage
    ) {
        validateCourseItemContext(courseId, itemId, executionPackage);
        if (executionPackage.moduleId() == null) {
            throw new IllegalStateException("CourseService returned empty moduleId");
        }
        String itemType = nullableUpper(executionPackage.itemType());
        if (!"CODING".equals(itemType) && !"SQL".equals(itemType)) {
            throw unprocessable("Item type is not runnable");
        }
        if (executionPackage.tests() == null || executionPackage.tests().isEmpty()) {
            throw unprocessable("Execution package has no tests");
        }
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

    private List<CourseItemExecutionPackage.ExecutionTest> openTests(CourseItemExecutionPackage executionPackage) {
        return executionPackage.tests()
                .stream()
                .filter(test -> !hasText(test.visibility()) || "OPEN".equalsIgnoreCase(test.visibility()))
                .toList();
    }

    private String resolveRunCode(CourseItemExecutionPackage executionPackage, RunItemRequest request) {
        String itemType = nullableUpper(executionPackage.itemType());
        String code = "SQL".equals(itemType)
                ? request.getSql()
                : request.getSourceCode();
        if (!hasText(code)) {
            throw unprocessable("Runnable source is required");
        }
        return code;
    }

    private String resolveRunLanguage(CourseItemExecutionPackage executionPackage) {
        if (hasText(executionPackage.language())) {
            return executionPackage.language();
        }
        if ("SQL".equals(nullableUpper(executionPackage.itemType()))) {
            return "sql";
        }
        throw unprocessable("Course item language is required");
    }

    private List<Long> normalizeOptionIds(List<Long> optionIds) {
        if (optionIds == null || optionIds.isEmpty()) {
            return List.of();
        }
        return optionIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(TreeSet::new))
                .stream()
                .toList();
    }

    private String optionIdsSnapshot(List<Long> optionIds) {
        return normalizeOptionIds(optionIds).stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",", "[", "]"));
    }

    private boolean sameIds(List<Long> first, List<Long> second) {
        return new TreeSet<>(first).equals(new TreeSet<>(second));
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

    private int reserveNextSubmissionNumber(Long userId, Long taskId) {
        lockUserTaskSubmissionSequence(userId, taskId);
        return nextSubmissionNumber(userId, taskId);
    }

    private void lockUserTaskSubmissionSequence(Long userId, Long taskId) {
        String lockKey = userId + ":" + taskId;
        entityManager.createNativeQuery("select pg_advisory_xact_lock(hashtextextended(?1, 0))")
                .setParameter(1, lockKey)
                .getSingleResult();
    }

    private void markTaskProgressOnSubmit(TaskProgress taskProgress, LocalDateTime now) {
        taskProgress.setAttemptsCount(orZero(taskProgress.getAttemptsCount()) + 1);
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

    private TaskProgress createTaskProgress(Long userId, Long courseId, Long moduleId, Long itemId) {
        LocalDateTime now = LocalDateTime.now();
        TaskProgress taskProgress = new TaskProgress();
        taskProgress.setUserId(userId);
        taskProgress.setCourseId(courseId);
        taskProgress.setModuleId(moduleId);
        taskProgress.setTaskId(itemId);
        taskProgress.setStatus(ProgressStatus.NOT_STARTED);
        taskProgress.setAttemptsCount(0);
        taskProgress.setBestScore(0);
        taskProgress.setLastScore(0);
        taskProgress.setIsCompleted(false);
        taskProgress.setFirstOpenedAt(now);
        taskProgress.setLastActivityAt(now);
        return taskProgressRepository.save(taskProgress);
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

    private void markTaskProgressOnRun(TaskProgress taskProgress, LocalDateTime now) {
        taskProgress.setLastActivityAt(now);
        if (taskProgress.getFirstOpenedAt() == null) {
            taskProgress.setFirstOpenedAt(now);
        }
        if (taskProgress.getStartedAt() == null) {
            taskProgress.setStartedAt(now);
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
        return buildExecutorRequest(
                executionPackage,
                executionPackage.tests(),
                request.getLanguage(),
                request.getSourceCode(),
                submission
        );
    }

    private ExecutionCreateRequest buildExecutorRequest(
            CourseItemExecutionPackage executionPackage,
            List<CourseItemExecutionPackage.ExecutionTest> executionTests,
            String language,
            String code,
            TaskSubmission submission
    ) {
        List<ExecutionCreateRequest.TestInput> tests = executionTests
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
                language,
                code,
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

    private ExecutionOutcome persistExecutionResults(
            TaskSubmission submission,
            CourseItemExecutionPackage executionPackage,
            List<CourseItemExecutionPackage.ExecutionTest> tests,
            ExecutionResponse executionResponse
    ) {
        Map<String, ExecutionResponse.TestExecutionResult> executionResultByKey =
                indexExecutionResults(executionResponse == null ? null : executionResponse.tests());

        List<SubmissionTestResult> savedResults = new ArrayList<>();
        int passedCount = 0;
        boolean hasCompareFail = false;
        boolean hasCompilationError = false;
        boolean hasRuntimeError = false;
        boolean hasTimeout = false;
        boolean hasMemoryLimit = false;
        boolean hasInternalError = false;

        for (CourseItemExecutionPackage.ExecutionTest test : tests) {
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
                } else if ("COMPILATION_ERROR".equals(outcome) || "COMPILE_ERROR".equals(outcome)) {
                    row.setStatus(TestResultStatus.ERROR);
                    hasCompilationError = true;
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

        return new ExecutionOutcome(
                savedResults,
                passedCount,
                tests.size(),
                hasCompareFail,
                hasCompilationError,
                hasRuntimeError,
                hasTimeout,
                hasMemoryLimit,
                hasInternalError
        );
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

    private void updateTaskProgressAfterRun(TaskProgress taskProgress, int score) {
        taskProgress.setLastScore(score);
        taskProgress.setLastActivityAt(LocalDateTime.now());
        if (!Boolean.TRUE.equals(taskProgress.getIsCompleted())) {
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
            boolean hasCompilationError,
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
        if (hasCompilationError) {
            return SubmissionVerdict.CE;
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
            normalized = normalized
                    .replaceAll("[\\t ]+(?=\\n|$)", "")
                    .replaceAll("\\s+$", "");
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

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private int textLength(String value) {
        return value == null ? 0 : value.length();
    }

    private int listSize(List<?> value) {
        return value == null ? 0 : value.size();
    }

    private ResponseStatusException unprocessable(String message) {
        return new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, message);
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

    private SubmissionResultResponse toSubmissionResultResponse(
            TaskSubmission submission,
            List<SubmissionTestResult> results,
            Map<String, String> visibilityByTestKey
    ) {
        return new SubmissionResultResponse(
                submission.getId(),
                submission.getTaskId(),
                toSubmissionResultStatus(submission),
                submission.getScore() == null ? null : submission.getScore().doubleValue(),
                submission.getPassedTestsCount(),
                submission.getTotalTestsCount(),
                firstActualOutput(results),
                firstErrorOutput(submission, results),
                results.stream()
                        .map(result -> toSubmissionTestResultResponse(result, visibilityByTestKey))
                        .toList(),
                submission.getCreatedAt()
        );
    }

    private SubmissionHistoryItemResponse toSubmissionHistoryItemResponse(TaskSubmission submission) {
        return new SubmissionHistoryItemResponse(
                submission.getId(),
                submission.getTaskId(),
                toSubmissionResultStatus(submission),
                submission.getScore() == null ? null : submission.getScore().doubleValue(),
                submission.getPassedTestsCount(),
                submission.getTotalTestsCount(),
                submission.getCreatedAt()
        );
    }

    private SubmissionTestResultResponse toSubmissionTestResultResponse(
            SubmissionTestResult result,
            Map<String, String> visibilityByTestKey
    ) {
        return new SubmissionTestResultResponse(
                result.getTestKey(),
                visibilityByTestKey.getOrDefault(result.getTestKey(), "OPEN"),
                result.getStatus() == TestResultStatus.PASSED,
                result.getActualOutput(),
                resultMessage(result),
                result.getExecutionTimeMs() == null ? null : result.getExecutionTimeMs().longValue(),
                result.getMemoryKb() == null ? null : result.getMemoryKb() / 1024.0
        );
    }

    private SubmissionResultStatus toSubmissionResultStatus(TaskSubmission submission) {
        if (submission.getStatus() == SubmissionStatus.QUEUED) {
            return SubmissionResultStatus.PENDING;
        }
        if (submission.getStatus() == SubmissionStatus.RUNNING) {
            return SubmissionResultStatus.RUNNING;
        }
        if (submission.getStatus() == SubmissionStatus.FAILED) {
            return SubmissionResultStatus.SYSTEM_ERROR;
        }

        SubmissionVerdict verdict = submission.getVerdict();
        if (verdict == SubmissionVerdict.OK) {
            return SubmissionResultStatus.ACCEPTED;
        }
        if (verdict == SubmissionVerdict.WA || verdict == SubmissionVerdict.PE) {
            return SubmissionResultStatus.WRONG_ANSWER;
        }
        if (verdict == SubmissionVerdict.CE) {
            return SubmissionResultStatus.COMPILATION_ERROR;
        }
        if (verdict == SubmissionVerdict.RE) {
            return SubmissionResultStatus.RUNTIME_ERROR;
        }
        if (verdict == SubmissionVerdict.TL) {
            return SubmissionResultStatus.TIME_LIMIT;
        }
        if (verdict == SubmissionVerdict.ML) {
            return SubmissionResultStatus.MEMORY_LIMIT;
        }
        return SubmissionResultStatus.SYSTEM_ERROR;
    }

    private String resultMessage(SubmissionTestResult result) {
        if (result.getStatus() == TestResultStatus.PASSED) {
            return null;
        }
        if (hasText(result.getErrorOutput())) {
            return result.getErrorOutput();
        }
        if (result.getStatus() == TestResultStatus.FAILED) {
            return "Wrong answer";
        }
        if (result.getStatus() == TestResultStatus.SKIPPED) {
            return "Test was not executed";
        }
        return "Execution error";
    }

    private String firstActualOutput(List<SubmissionTestResult> results) {
        return results.stream()
                .map(SubmissionTestResult::getActualOutput)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private String firstErrorOutput(TaskSubmission submission, List<SubmissionTestResult> results) {
        if (hasText(submission.getErrorMessage())) {
            return submission.getErrorMessage();
        }
        return results.stream()
                .map(SubmissionTestResult::getErrorOutput)
                .filter(this::hasText)
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> visibilityByTestKey(List<CourseItemExecutionPackage.ExecutionTest> tests) {
        return tests.stream()
                .collect(Collectors.toMap(
                        CourseItemExecutionPackage.ExecutionTest::testKey,
                        test -> hasText(test.visibility()) ? test.visibility() : "OPEN",
                        (first, ignored) -> first
                ));
    }

    private Map<String, String> visibilityByTestKey(CourseItemExecutionPackage executionPackage) {
        if (executionPackage == null || executionPackage.tests() == null || executionPackage.tests().isEmpty()) {
            return Map.of();
        }
        return visibilityByTestKey(executionPackage.tests());
    }

    private record ExecutionOutcome(
            List<SubmissionTestResult> results,
            int passedCount,
            int totalCount,
            boolean hasCompareFail,
            boolean hasCompilationError,
            boolean hasRuntimeError,
            boolean hasTimeout,
            boolean hasMemoryLimit,
            boolean hasInternalError
    ) {
    }
}
