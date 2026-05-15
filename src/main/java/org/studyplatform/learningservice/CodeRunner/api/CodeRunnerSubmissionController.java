package org.studyplatform.learningservice.CodeRunner.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.studyplatform.learningservice.CodeRunner.service.CodeRunnerSubmissionService;

@RestController
@Validated
@RequestMapping("/api/v1/learning/tasks")
public class CodeRunnerSubmissionController {

    private final CodeRunnerSubmissionService service;

    public CodeRunnerSubmissionController(CodeRunnerSubmissionService service) {
        this.service = service;
    }

    @PostMapping("/{taskId}/submissions")
    @ResponseStatus(HttpStatus.CREATED)
    public SubmissionCreateResponse submit(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader,
            @PathVariable @Positive Long taskId,
            @Valid @RequestBody SubmissionCreateRequest request
    ) {
        Long userId = resolveUserId(jwt);
        return service.submit(userId, taskId, authorizationHeader, request);
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
