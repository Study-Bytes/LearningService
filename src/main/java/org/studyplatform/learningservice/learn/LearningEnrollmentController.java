package org.studyplatform.learningservice.learn;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
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
        return learningEnrollmentService.enroll(resolveUserId(jwt), courseId);
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
}
