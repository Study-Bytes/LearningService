package org.studyplatform.learningservice.moduleprogress;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.studyplatform.learningservice.common.ProgressStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ModuleProgressCreateRequest {

    @NotNull
    @Positive
    private Long userId;

    @NotNull
    @Positive
    private Long courseId;

    @NotNull
    @Positive
    private Long moduleId;

    private ProgressStatus status;

    @DecimalMin(value = "0.00")
    @DecimalMax(value = "100.00")
    private BigDecimal progressPercent;

    @PositiveOrZero
    private Integer completedTasksCount;

    @PositiveOrZero
    private Integer totalTasksCount;

    @PositiveOrZero
    private Integer score;

    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime lastActivityAt;

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

    public ProgressStatus getStatus() {
        return status;
    }

    public void setStatus(ProgressStatus status) {
        this.status = status;
    }

    public BigDecimal getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(BigDecimal progressPercent) {
        this.progressPercent = progressPercent;
    }

    public Integer getCompletedTasksCount() {
        return completedTasksCount;
    }

    public void setCompletedTasksCount(Integer completedTasksCount) {
        this.completedTasksCount = completedTasksCount;
    }

    public Integer getTotalTasksCount() {
        return totalTasksCount;
    }

    public void setTotalTasksCount(Integer totalTasksCount) {
        this.totalTasksCount = totalTasksCount;
    }

    public Integer getScore() {
        return score;
    }

    public void setScore(Integer score) {
        this.score = score;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public LocalDateTime getLastActivityAt() {
        return lastActivityAt;
    }

    public void setLastActivityAt(LocalDateTime lastActivityAt) {
        this.lastActivityAt = lastActivityAt;
    }
}
