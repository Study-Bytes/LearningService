package org.studyplatform.learningservice.learn;

import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Validated
@RequestMapping("/api/v1/learn/courses")
public class LearningContentCompletionController {

    private final LearningContentCompletionService service;

    public LearningContentCompletionController(LearningContentCompletionService service) {
        this.service = service;
    }

    @PostMapping("/{courseId}/items/{itemId}/complete")
    public LearningItemCompletionResponse completeContentItem(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long itemId
    ) {
        return service.completeContentItem(resolveUserId(jwt), courseId, itemId, authorizationHeader);
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
