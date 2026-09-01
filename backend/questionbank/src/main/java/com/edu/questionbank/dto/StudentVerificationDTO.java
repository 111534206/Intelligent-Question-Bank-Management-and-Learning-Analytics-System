package com.edu.questionbank.dto;

import java.util.List;

public class StudentVerificationDTO {

    /** 待驗證學生輸入資訊 */
    public static class StudentItem {
        private String studentNo;
        private String name;
        private String seatNo;
        private String email;
        private String note;
        private boolean verified;
        private Long userId;
        private String statusMessage;

        public StudentItem() {}

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
        public boolean isVerified() { return verified; }
        public void setVerified(boolean verified) { this.verified = verified; }
        public Long getUserId() { return userId; }
        public void setUserId(Long userId) { this.userId = userId; }
        public String getStatusMessage() { return statusMessage; }
        public void setStatusMessage(String statusMessage) { this.statusMessage = statusMessage; }
    }

    /** 批次驗證結果 */
    public static class VerificationResult {
        private int totalCount;
        private int verifiedCount;
        private int unverifiedCount;
        private List<StudentItem> students;

        public VerificationResult(int totalCount, int verifiedCount, int unverifiedCount, List<StudentItem> students) {
            this.totalCount = totalCount;
            this.verifiedCount = verifiedCount;
            this.unverifiedCount = unverifiedCount;
            this.students = students;
        }

        public int getTotalCount() { return totalCount; }
        public int getVerifiedCount() { return verifiedCount; }
        public int getUnverifiedCount() { return unverifiedCount; }
        public List<StudentItem> getStudents() { return students; }
    }
}
