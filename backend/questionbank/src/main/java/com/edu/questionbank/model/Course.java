package com.edu.questionbank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 課程實體 — 僅用於 JPA 自建的 qb_courses 資料表（與既有 dbo.Courses 分離）
 * CourseController 使用 JdbcTemplate 直接操作 dbo.Courses，不走此 Entity
 */
@Entity
@Table(name = "qb_courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", length = 200)
    private String name;

    @Column(name = "code", length = 100)
    private String code;

    @Column(name = "type", length = 50)
    private String type;

    @Column(name = "academic_year", length = 20)
    private String year;

    @Column(name = "semester", length = 20)
    private String semester;

    @Column(name = "credits", length = 20)
    private String credits;

    @Column(name = "grade", length = 50)
    private String grade;

    @Column(name = "class_group", length = 50)
    private String classGroup;

    @Column(name = "teacher", length = 100)
    private String teacher;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
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
