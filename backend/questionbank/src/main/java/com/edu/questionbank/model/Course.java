package com.edu.questionbank.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 課程實體 — 對應 courses 表
 */
@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "課程名稱不可空白")
    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @NotBlank(message = "課號不可空白")
    @Column(name = "code", length = 100, nullable = false)
    private String code;

    @Column(name = "type", length = 50)
    private String type; // 必修 / 選修 / 共同必修 / 通識必修 / 通識選修 / 專業必修 / 專業選修

    @Column(name = "academic_year", length = 20)
    private String year; // 113, 114 等學年度

    @Column(name = "semester", length = 20)
    private String semester; // 1, 2, 第一學期等

    @Column(name = "credits", length = 20)
    private String credits; // 3, 2, 4.0 等學分數

    @Column(name = "grade", length = 50)
    private String grade; // 高二, 一年級, 大一等

    @Column(name = "class_group", length = 50)
    private String classGroup; // 甲班, 乙班, 綜合班等

    @Column(name = "teacher", length = 100)
    private String teacher; // 授課教師

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 此課程的所有修課學生（OneToMany） */
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JsonManagedReference
    private List<CourseStudent> students = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void addStudent(CourseStudent student) {
        students.add(student);
        student.setCourse(this);
    }

    public void removeStudent(CourseStudent student) {
        students.remove(student);
        student.setCourse(null);
    }

    // ── Getters & Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public String getYear() { return year; }
    public void setYear(String year) { this.year = year; }
    public String getSemester() { return semester; }
    public void setSemester(String semester) { this.semester = semester; }
    public String getCredits() { return credits; }
    public void setCredits(String credits) { this.credits = credits; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public String getClassGroup() { return classGroup; }
    public void setClassGroup(String classGroup) { this.classGroup = classGroup; }
    public String getTeacher() { return teacher; }
    public void setTeacher(String teacher) { this.teacher = teacher; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public List<CourseStudent> getStudents() { return students; }
    public void setStudents(List<CourseStudent> students) { this.students = students; }
}
