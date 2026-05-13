package org.studyplatform.learningservice.taskprogress;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskProgressRepository extends JpaRepository<TaskProgress, Long> {

    boolean existsByUserIdAndTaskId(Long userId, Long taskId);

    Optional<TaskProgress> findByUserIdAndCourseIdAndModuleIdAndTaskId(
            Long userId,
            Long courseId,
            Long moduleId,
            Long taskId
    );
}
