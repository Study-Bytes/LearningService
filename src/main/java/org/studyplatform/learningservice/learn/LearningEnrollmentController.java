package org.studyplatform.learningservice.learn;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping("/api/v1/learn/courses")
public class LearningEnrollmentController {

    private final LearningEnrollmentService learningEnrollmentService;

    public LearningEnrollmentController(LearningEnrollmentService learningEnrollmentService) {
        this.learningEnrollmentService = learningEnrollmentService;
    }

    @PostMapping("/{courseId}/enroll")
    public EnrollCourseResponse enroll(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long courseId
    ) {
        return learningEnrollmentService.enroll(resolveUserId(jwt), courseId, resolveNickname(jwt));
    }

    @GetMapping("/{courseId}")
    public LearningCourseStateResponse getCourseState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long courseId
    ) {
        return learningEnrollmentService.getCourseState(resolveUserId(jwt), courseId);
    }

    @GetMapping("/{courseId}/leaderboard")
    public CourseLeaderboardResponse getCourseLeaderboard(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long courseId
    ) {
        return learningEnrollmentService.getCourseLeaderboard(resolveUserId(jwt), courseId, resolveNickname(jwt));
    }

    @GetMapping("/{courseId}/items/{itemId}")
    public LearningItemStateResponse getItemState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long itemId
    ) {
        return learningEnrollmentService.getItemState(resolveUserId(jwt), courseId, itemId);
    }

    private Long resolveUserId(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject is missing");
        }
        try {
            Long userId = Long.valueOf(jwt.getSubject());
            if (userId <= 0) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must be a positive user id");
            }
            return userId;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "JWT subject must be a numeric user id");
        }
    }

    private String resolveNickname(Jwt jwt) {
        if (jwt == null) {
            return null;
        }
        for (String claim : new String[]{"nickname", "nick", "username", "preferred_username", "name", "email"}) {
            String value = jwt.getClaimAsString(claim);
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
