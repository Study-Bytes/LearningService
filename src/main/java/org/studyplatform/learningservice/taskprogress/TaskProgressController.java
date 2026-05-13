package org.studyplatform.learningservice.taskprogress;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Validated
@RequestMapping("/api/v1/learning/task-progress")
public class TaskProgressController {

    private final TaskProgressService service;

    public TaskProgressController(TaskProgressService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public TaskProgressResponse create(@Valid @RequestBody TaskProgressCreateRequest request) {
        return service.create(request);
    }

    @PutMapping({"/{userId}/{courseId}/{moduleId}/{taskId}", "/{userId}/{courseId}/{moduleId}/{taskId}/"})
    public TaskProgressResponse update(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long moduleId,
            @PathVariable @Positive Long taskId,
            @Valid @RequestBody TaskProgressUpdateRequest request
    ) {
        return service.update(userId, courseId, moduleId, taskId, request);
    }

    @GetMapping({"/{userId}/{courseId}/{moduleId}/{taskId}", "/{userId}/{courseId}/{moduleId}/{taskId}/"})
    public TaskProgressResponse getByUserCourseModuleAndTask(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long moduleId,
            @PathVariable @Positive Long taskId
    ) {
        return service.getByUserCourseModuleAndTask(userId, courseId, moduleId, taskId);
    }
}
