package org.studyplatform.learningservice.taskprogress;

import jakarta.validation.constraints.PositiveOrZero;
import org.studyplatform.learningservice.common.ProgressStatus;

import java.time.LocalDateTime;

public class TaskProgressUpdateRequest {

    private ProgressStatus status;

    @PositiveOrZero
    private Integer attemptsCount;

    @PositiveOrZero
    private Integer bestScore;

    @PositiveOrZero
    private Integer lastScore;

    private Boolean isCompleted;

    private LocalDateTime firstOpenedAt;
    private LocalDateTime startedAt;
    private LocalDateTime firstSuccessAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastSubmissionAt;
    private LocalDateTime lastActivityAt;

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public Integer getAttemptsCount() {
        return attemptsCount;
    }

    public void setAttemptsCount(Integer attemptsCount) {
        this.attemptsCount = attemptsCount;
    }

    public Integer getBestScore() {
        return bestScore;
    }

    public void setBestScore(Integer bestScore) {
        this.bestScore = bestScore;
    }

    public Integer getLastScore() {
        return lastScore;
    }

    public void setLastScore(Integer lastScore) {
        this.lastScore = lastScore;
    }

    public Boolean getIsCompleted() {
        return isCompleted;
    }

    public void setIsCompleted(Boolean completed) {
        isCompleted = completed;
    }

    public LocalDateTime getFirstOpenedAt() {
        return firstOpenedAt;
    }

    public void setFirstOpenedAt(LocalDateTime firstOpenedAt) {
        this.firstOpenedAt = firstOpenedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFirstSuccessAt() {
        return firstSuccessAt;
    }

    public void setFirstSuccessAt(LocalDateTime firstSuccessAt) {
        this.firstSuccessAt = firstSuccessAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getLastSubmissionAt() {
        return lastSubmissionAt;
    }

    public void setLastSubmissionAt(LocalDateTime lastSubmissionAt) {
        this.lastSubmissionAt = lastSubmissionAt;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
