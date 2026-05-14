package org.studyplatform.learningservice.CodeRunner.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
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
            @RequestHeader("user_id") @Positive Long userId,
            @PathVariable @Positive Long taskId,
            @Valid @RequestBody SubmissionCreateRequest request
    ) {
        return service.submit(userId, taskId, request);
    }
}
