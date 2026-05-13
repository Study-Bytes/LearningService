package org.studyplatform.learningservice.moduleprogress;

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
@RequestMapping("/api/v1/learning/module-progress")
public class ModuleProgressController {

    private final ModuleProgressService service;

    public ModuleProgressController(ModuleProgressService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public ModuleProgressResponse create(@Valid @RequestBody ModuleProgressCreateRequest request) {
        return service.create(request);
    }

    @PutMapping({"/{userId}/{courseId}/{moduleId}", "/{userId}/{courseId}/{moduleId}/"})
    public ModuleProgressResponse update(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long moduleId,
            @Valid @RequestBody ModuleProgressUpdateRequest request
    ) {
        return service.update(userId, courseId, moduleId, request);
    }

    @GetMapping({"/{userId}/{courseId}/{moduleId}", "/{userId}/{courseId}/{moduleId}/"})
    public ModuleProgressResponse getByUserCourseAndModule(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long courseId,
            @PathVariable @Positive Long moduleId
    ) {
        return service.getByUserCourseAndModule(userId, courseId, moduleId);
    }
}
