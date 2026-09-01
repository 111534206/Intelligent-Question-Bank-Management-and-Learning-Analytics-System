package com.edu.questionbank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 題目傳輸物件 — 用於新增 / 修改題目的請求體
 */
public class QuestionDTO {

    @NotBlank(message = "題目內容不可空白")
    private String content;

    @NotBlank(message = "選項 A 不可空白")
    private String optA;

    @NotBlank(message = "選項 B 不可空白")
    private String optB;

    @NotBlank(message = "選項 C 不可空白")
    private String optC;

    @NotBlank(message = "選項 D 不可空白")
    private String optD;

    @Pattern(regexp = "[ABCD]", message = "答案必須為 A、B、C 或 D")
    private String answer;

    private String subject;
    private String unit;
    private String dept;       // 科系
    private String difficulty; // 易 / 中 / 難
    private String source;     // 來源類型

    // ── Getters & Setters ──────────────────────────────────────

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
    public String getAnswer() { return answer; }
    public void setAnswer(String answer) { this.answer = answer; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public String getDept() { return dept; }
    public void setDept(String dept) { this.dept = dept; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
