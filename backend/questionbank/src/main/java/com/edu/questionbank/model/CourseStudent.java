package com.edu.questionbank.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 課程修課學生實體 — 對應 course_students 表
 */
@Entity
@Table(name = "course_students")
public class CourseStudent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    @JsonBackReference
    private Course course;

    @Column(name = "student_no", length = 50, nullable = false)
    private String studentNo;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "seat_no", length = 20)
    private String seatNo;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "note", length = 255)
    private String note;

    @Column(name = "is_assistant")
    private Boolean isAssistant = false;

    @Column(name = "assistant_permissions", length = 500)
    private String assistantPermissions;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }
    public String getStudentNo() { return studentNo; }
    public void setStudentNo(String studentNo) { this.studentNo = studentNo; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSeatNo() { return seatNo; }
    public void setSeatNo(String seatNo) { this.seatNo = seatNo; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Boolean getIsAssistant() { return isAssistant != null && isAssistant; }
    public void setIsAssistant(Boolean isAssistant) { this.isAssistant = isAssistant; }
    public String getAssistantPermissions() { return assistantPermissions; }
    public void setAssistantPermissions(String assistantPermissions) { this.assistantPermissions = assistantPermissions; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
