package com.edu.questionbank.dto;

/**
 * 課程修課學生 DTO（含小老師標記與權限清單）
 */
public class CourseStudentDTO {

    private Long id;
    private String studentNo;
    private String name;
    private String seatNo;
    private String email;
    private String note;
    private Boolean isAssistant = false;
    private String assistantPermissions;

    public CourseStudentDTO() {}

    public CourseStudentDTO(Long id, String studentNo, String name, String seatNo, String email, String note) {
        this.id = id;
        this.studentNo = studentNo;
        this.name = name;
        this.seatNo = seatNo;
        this.email = email;
        this.note = note;
        this.isAssistant = false;
    }

    public CourseStudentDTO(Long id, String studentNo, String name, String seatNo, String email, String note, Boolean isAssistant, String assistantPermissions) {
        this.id = id;
        this.studentNo = studentNo;
        this.name = name;
        this.seatNo = seatNo;
        this.email = email;
        this.note = note;
        this.isAssistant = isAssistant != null && isAssistant;
        this.assistantPermissions = assistantPermissions;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
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
}
