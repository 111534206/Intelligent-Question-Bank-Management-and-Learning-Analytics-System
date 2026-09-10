package com.edu.questionbank.dto;

import java.time.LocalDateTime;
import java.util.List;

public class PaperDTO {

    private Long id;
    private String name;
    private String subject;
    private String department;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String targetAudience;
    private Boolean showScoreImmediately;
    private Boolean showAnswerImmediately;
    private Boolean showRankImmediately;
    private String questionIds;
    private Integer questionCount;
    private Integer durationMinutes;
    private String createdBy;
    private LocalDateTime createdAt;
    private List<QuestionDTO> questions;

    // Status for student view: UPCOMING (未開始), ACTIVE (進行中), ENDED (已截止)
    private String status;

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

    public List<QuestionDTO> getQuestions() { return questions; }
    public void setQuestions(List<QuestionDTO> questions) { this.questions = questions; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
