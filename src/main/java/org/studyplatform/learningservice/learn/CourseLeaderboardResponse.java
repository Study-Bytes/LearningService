package org.studyplatform.learningservice.learn;

import java.util.List;

public record CourseLeaderboardResponse(
        Long courseId,
        LeaderboardViewerRole viewerRole,
        List<CourseLeaderboardEntryResponse> top,
        CourseLeaderboardEntryResponse currentUser
) {
}
