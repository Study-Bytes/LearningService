package org.studyplatform.learningservice.CodeRunner.client.course;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QuizEvaluationPackage(
        Long itemId,
        Long moduleId,
        Long courseId,
        String itemType,
        String title,
        List<QuizOption> options
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuizOption(
            Long id,
            Integer orderIndex,
            String label,
            String text,
            Boolean correct,
            String explanation
    ) {
    }
}
