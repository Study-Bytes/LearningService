package org.studyplatform.learningservice.learn;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/v1/learn")
public class LearningMyCoursesController {

    private final LearningEnrollmentService learningEnrollmentService;

    public LearningMyCoursesController(LearningEnrollmentService learningEnrollmentService) {
        this.learningEnrollmentService = learningEnrollmentService;
    }

    @GetMapping("/my-courses")
    public List<MyCourseEnrollmentResponse> getMyCourses(@AuthenticationPrincipal Jwt jwt) {
        return learningEnrollmentService.getMyCourses(resolveUserId(jwt));
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
