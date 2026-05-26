package org.studyplatform.learningservice.learn;

import java.util.List;

public record CourseLeaderboardResponse(
        Long courseId,
        List<CourseLeaderboardEntryResponse> top,
        CourseLeaderboardEntryResponse currentUser
) {
}
