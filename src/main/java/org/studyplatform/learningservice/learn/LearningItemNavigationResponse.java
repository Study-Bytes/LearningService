package org.studyplatform.learningservice.learn;

public record LearningItemNavigationResponse(
        Long previousItemId,
        Long nextItemId
) {
}
