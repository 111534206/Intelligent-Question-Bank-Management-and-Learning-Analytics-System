package com.edu.questionbank.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 固定試卷實體 — 對應資料庫 papers 表
 */
@Entity
@Table(name = "papers")
public class Paper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "試卷名稱不可空白")
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "subject", length = 100)
    private String subject;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "start_time")
    private LocalDateTime startTime;

    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 開放對象：ALL (全部) 或 班級清單 (逗號分隔，例如："忠班,孝班")
     */
    @Column(name = "target_audience", length = 500)
    private String targetAudience = "ALL";

    /**
     * 是否在交卷後立刻顯示成績
     */
    @Column(name = "show_score_immediately")
    private Boolean showScoreImmediately = true;

    /**
     * 是否在交卷後立刻顯示答案
     */
    @Column(name = "show_answer_immediately")
    private Boolean showAnswerImmediately = true;

    /**
     * 是否在交卷後顯示對開放對象的排名
     */
    @Column(name = "show_rank_immediately")
    private Boolean showRankImmediately = true;

    /**
     * 題目 ID 清單（JSON 或逗號分隔，例如："1,2,5,8"）
     */
    @Column(name = "question_ids", length = 4000)
    private String questionIds;

    @Column(name = "question_count")
    private Integer questionCount = 0;

    @Column(name = "duration_minutes")
    private Integer durationMinutes = 30;

    @Column(name = "created_by", length = 100)
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        if (this.showScoreImmediately == null) this.showScoreImmediately = true;
        if (this.showAnswerImmediately == null) this.showAnswerImmediately = true;
        if (this.showRankImmediately == null) this.showRankImmediately = true;
        if (this.targetAudience == null || this.targetAudience.isBlank()) this.targetAudience = "ALL";
    }

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public String getTargetAudience() { return targetAudience; }
    public void setTargetAudience(String targetAudience) { this.targetAudience = targetAudience; }

    public Boolean getShowScoreImmediately() { return showScoreImmediately; }
    public void setShowScoreImmediately(Boolean showScoreImmediately) { this.showScoreImmediately = showScoreImmediately; }

    public Boolean getShowAnswerImmediately() { return showAnswerImmediately; }
    public void setShowAnswerImmediately(Boolean showAnswerImmediately) { this.showAnswerImmediately = showAnswerImmediately; }

    public Boolean getShowRankImmediately() { return showRankImmediately; }
    public void setShowRankImmediately(Boolean showRankImmediately) { this.showRankImmediately = showRankImmediately; }

    public String getQuestionIds() { return questionIds; }
    public void setQuestionIds(String questionIds) { this.questionIds = questionIds; }

    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }

    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
