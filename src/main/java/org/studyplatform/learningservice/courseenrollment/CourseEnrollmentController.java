package org.studyplatform.learningservice.courseenrollment;

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
@RequestMapping("/api/v1/learning/course-enrollments")
public class CourseEnrollmentController {

    private final CourseEnrollmentService service;

    public CourseEnrollmentController(CourseEnrollmentService service) {
        this.service = service;
    }

    @PostMapping({"", "/"})
    @ResponseStatus(HttpStatus.CREATED)
    public CourseEnrollmentResponse create(@Valid @RequestBody CourseEnrollmentCreateRequest request) {
        return service.create(request);
    }

    @PutMapping({"/{userId}/{courseId}", "/{userId}/{courseId}/"})
    public CourseEnrollmentResponse update(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long courseId,
            @Valid @RequestBody CourseEnrollmentUpdateRequest request
    ) {
        return service.update(userId, courseId, request);
    }

    @GetMapping({"/{userId}/{courseId}", "/{userId}/{courseId}/"})
    public CourseEnrollmentResponse getByUserAndCourse(
            @PathVariable @Positive Long userId,
            @PathVariable @Positive Long courseId
    ) {
        return service.getByUserAndCourse(userId, courseId);
    }
}
