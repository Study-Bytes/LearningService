package org.studyplatform.learningservice.CodeRunner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface TaskSubmissionRepository extends JpaRepository<TaskSubmission, Long> {

    Optional<TaskSubmission> findTopByUserIdAndTaskIdOrderBySubmissionNumberDesc(Long userId, Long taskId);
}
