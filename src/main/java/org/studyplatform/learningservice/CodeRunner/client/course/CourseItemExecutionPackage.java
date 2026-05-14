package org.studyplatform.learningservice.CodeRunner.client.course;

import java.util.List;

public record CourseItemExecutionPackage(
        Long itemId,
        Long moduleId,
        Long courseId,
        String itemType,
        String title,
        String language,
        String starterCode,
        ExecutionLimits limits,
        ExecutionPolicy executionPolicy,
        EvaluationPolicy evaluationPolicy,
        List<ExecutionTest> tests
) {

    public record ExecutionLimits(
            Integer timeLimitMs,
            Integer memoryLimitMb,
            Integer outputLimitKb
    ) {
    }

    public record ExecutionPolicy(
            Boolean networkDisabled,
            Boolean readOnlyFs
    ) {
    }

    public record EvaluationPolicy(
            String comparisonMode,
            Boolean normalizeLineEndings,
            Boolean trimTrailingWhitespaces
    ) {
    }

    public record ExecutionTest(
            String testKey,
            String visibility,
            String inputData,
            String expectedOutput,
            Integer orderIndex
    ) {
    }
}
