package org.studyplatform.learningservice.learn;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CourseItemContentResponse(
        Long itemId,
        Long moduleId,
        Long courseId,
        String itemType,
        String title
) {
}
