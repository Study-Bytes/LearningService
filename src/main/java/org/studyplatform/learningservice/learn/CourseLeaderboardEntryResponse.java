package org.studyplatform.learningservice.learn;

import java.math.BigDecimal;

public record CourseLeaderboardEntryResponse(
        Long userId,
        Integer place,
        String nickname,
        BigDecimal progressPercent
) {
}
