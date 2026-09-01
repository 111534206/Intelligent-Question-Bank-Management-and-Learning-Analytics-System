package com.edu.questionbank.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 課程資料傳輸物件 DTO
 */
public class CourseDTO {

    private Long id;
    private String name;
    private String code;
    private String type;
    private String year;
    private String semester;
    private String credits;
    private String grade;
    private String classGroup;
    private String teacher;
    private String createdAt;
    private int studentCount;
    private List<CourseStudentDTO> students = new ArrayList<>();

    public CourseDTO() {}

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
    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
    public int getStudentCount() { return studentCount; }
    public void setStudentCount(int studentCount) { this.studentCount = studentCount; }
    public List<CourseStudentDTO> getStudents() { return students; }
    public void setStudents(List<CourseStudentDTO> students) { this.students = students; }
}
