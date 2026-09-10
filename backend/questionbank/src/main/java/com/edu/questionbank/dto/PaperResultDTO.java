package com.edu.questionbank.dto;

import java.util.List;
import java.util.Map;

public class PaperResultDTO {

    private Long submissionId;
    private Long paperId;
    private String paperName;
    private Integer score;
    private Integer totalQuestions;
    private Integer correctCount;
    private Double accuracy;

    private Boolean showScore;
    private Boolean showAnswer;
    private Boolean showRank;

    // Rank info (e.g. rank 1 among 25 students)
    private Integer rank;
    private Integer totalStudentsInAudience;
    private String rankPercentile;

    // Detailed question reviews (if showAnswer is true)
    private List<QuestionReviewItem> questionReviews;

    public static class QuestionReviewItem {
        private Long questionId;
        private String content;
        private String optA;
        private String optB;
        private String optC;
        private String optD;
        private String studentAnswer;
        private String correctAnswer;
        private Boolean isCorrect;

        public Long getQuestionId() { return questionId; }
        public void setQuestionId(Long questionId) { this.questionId = questionId; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }

        public String getOptA() { return optA; }
        public void setOptA(String optA) { this.optA = optA; }

        public String getOptB() { return optB; }
        public void setOptB(String optB) { this.optB = optB; }

        public String getOptC() { return optC; }
        public void setOptC(String optC) { this.optC = optC; }

        public String getOptD() { return optD; }
        public void setOptD(String optD) { this.optD = optD; }

        public String getStudentAnswer() { return studentAnswer; }
        public void setStudentAnswer(String studentAnswer) { this.studentAnswer = studentAnswer; }

        public String getCorrectAnswer() { return correctAnswer; }
        public void setCorrectAnswer(String correctAnswer) { this.correctAnswer = correctAnswer; }

        public Boolean getIsCorrect() { return isCorrect; }
        public void setIsCorrect(Boolean isCorrect) { this.isCorrect = isCorrect; }
    }

    public Long getSubmissionId() { return submissionId; }
    public void setSubmissionId(Long submissionId) { this.submissionId = submissionId; }

    public Long getPaperId() { return paperId; }
    public void setPaperId(Long paperId) { this.paperId = paperId; }

    public String getPaperName() { return paperName; }
    public void setPaperName(String paperName) { this.paperName = paperName; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getTotalQuestions() { return totalQuestions; }
    public void setTotalQuestions(Integer totalQuestions) { this.totalQuestions = totalQuestions; }

    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }

    public Double getAccuracy() { return accuracy; }
    public void setAccuracy(Double accuracy) { this.accuracy = accuracy; }

    public Boolean getShowScore() { return showScore; }
    public void setShowScore(Boolean showScore) { this.showScore = showScore; }

    public Boolean getShowAnswer() { return showAnswer; }
    public void setShowAnswer(Boolean showAnswer) { this.showAnswer = showAnswer; }

    public Boolean getShowRank() { return showRank; }
    public void setShowRank(Boolean showRank) { this.showRank = showRank; }

    public Integer getRank() { return rank; }
    public void setRank(Integer rank) { this.rank = rank; }

    public Integer getTotalStudentsInAudience() { return totalStudentsInAudience; }
    public void setTotalStudentsInAudience(Integer totalStudentsInAudience) { this.totalStudentsInAudience = totalStudentsInAudience; }

    public String getRankPercentile() { return rankPercentile; }
    public void setRankPercentile(String rankPercentile) { this.rankPercentile = rankPercentile; }

    public List<QuestionReviewItem> getQuestionReviews() { return questionReviews; }
    public void setQuestionReviews(List<QuestionReviewItem> questionReviews) { this.questionReviews = questionReviews; }
}
