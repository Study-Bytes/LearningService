package org.studyplatform.learningservice.moduleprogress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ModuleProgressRepository extends JpaRepository<ModuleProgress, Long> {

    boolean existsByUserIdAndModuleId(Long userId, Long moduleId);

    Optional<ModuleProgress> findByUserIdAndCourseIdAndModuleId(Long userId, Long courseId, Long moduleId);
}
