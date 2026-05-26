package org.studyplatform.learningservice.learn;

import jakarta.validation.constraints.Positive;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@RestController
@Validated
@RequestMapping("/api/v1/learn/courses")
public class LearningDeadlineController {

    private final LearningDeadlineService learningDeadlineService;

    public LearningDeadlineController(LearningDeadlineService learningDeadlineService) {
        this.learningDeadlineService = learningDeadlineService;
    }

    @GetMapping("/{courseId}/modules/{moduleId}/deadline-state")
    public ModuleDeadlineStateResponse getModuleDeadlineState(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long moduleId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime deadlineAt
    ) {
        return learningDeadlineService.getModuleDeadlineState(
                resolveUserId(jwt),
                courseId,
                moduleId,
                deadlineAt
        );
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
