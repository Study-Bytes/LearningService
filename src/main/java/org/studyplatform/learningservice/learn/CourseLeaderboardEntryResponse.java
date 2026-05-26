package org.studyplatform.learningservice.learn;

import java.math.BigDecimal;

public record CourseLeaderboardEntryResponse(
        Integer place,
        String nickname,
        BigDecimal progressPercent
) {
}
