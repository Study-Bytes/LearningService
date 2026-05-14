package org.studyplatform.learningservice.CodeRunner.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "task_submissions",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_task_submissions_user_task_number",
                        columnNames = {"user_id", "task_id", "submission_number"}
                )
        }
)
public class TaskSubmission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "course_id", nullable = false)
    private Long courseId;

    @Column(name = "module_id", nullable = false)
    private Long moduleId;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "submission_number", nullable = false)
    private Integer submissionNumber;

    @Column(name = "language", nullable = false, length = 50)
    private String language;

    @Column(name = "source_code", nullable = false, columnDefinition = "text")
    private String sourceCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SubmissionStatus status = SubmissionStatus.QUEUED;

    @Enumerated(EnumType.STRING)
    @Column(name = "verdict")
    private SubmissionVerdict verdict;

    @Column(name = "score", nullable = false)
    private Integer score = 0;

    @Column(name = "passed_tests_count", nullable = false)
    private Integer passedTestsCount = 0;

    @Column(name = "total_tests_count", nullable = false)
    private Integer totalTestsCount = 0;

    @Column(name = "executor_request_id", length = 255)
    private String executorRequestId;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "submitted_at", nullable = false)
    private LocalDateTime submittedAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (submittedAt == null) {
            submittedAt = now;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
        if (status == null) {
            status = SubmissionStatus.QUEUED;
        }
        if (score == null) {
            score = 0;
        }
        if (passedTestsCount == null) {
            passedTestsCount = 0;
        }
        if (totalTestsCount == null) {
            totalTestsCount = 0;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getCourseId() {
        return courseId;
    }

    public void setCourseId(Long courseId) {
        this.courseId = courseId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public void setModuleId(Long moduleId) {
        this.moduleId = moduleId;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Integer getSubmissionNumber() {
        return submissionNumber;
    }

    public void setSubmissionNumber(Integer submissionNumber) {
        this.submissionNumber = submissionNumber;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sourceCode) {
        this.sourceCode = sourceCode;
    }

    public SubmissionStatus getStatus() {
        return status;
    }

    public void setStatus(SubmissionStatus status) {
        this.status = status;
    }

    public SubmissionVerdict getVerdict() {
        return verdict;
    }

    public void setVerdict(SubmissionVerdict verdict) {
        this.verdict = verdict;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public Integer getPassedTestsCount() {
        return passedTestsCount;
    }

    public void setPassedTestsCount(Integer passedTestsCount) {
        this.passedTestsCount = passedTestsCount;
    }

    public Integer getTotalTestsCount() {
        return totalTestsCount;
    }

    public void setTotalTestsCount(Integer totalTestsCount) {
        this.totalTestsCount = totalTestsCount;
    }

    public String getExecutorRequestId() {
        return executorRequestId;
    }

    public void setExecutorRequestId(String executorRequestId) {
        this.executorRequestId = executorRequestId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
