package org.studyplatform.learningservice.CodeRunner.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubmissionTestResultRepository extends JpaRepository<SubmissionTestResult, Long> {

    List<SubmissionTestResult> findBySubmissionIdOrderByTestOrderAsc(Long submissionId);
}
