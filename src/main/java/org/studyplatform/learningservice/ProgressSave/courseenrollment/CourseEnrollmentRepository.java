package org.studyplatform.learningservice.courseenrollment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CourseEnrollmentRepository extends JpaRepository<CourseEnrollment, Long> {

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);

    Optional<CourseEnrollment> findByUserIdAndCourseId(Long userId, Long courseId);

    List<CourseEnrollment> findByUserIdOrderByCreatedAtDesc(Long userId);
}
