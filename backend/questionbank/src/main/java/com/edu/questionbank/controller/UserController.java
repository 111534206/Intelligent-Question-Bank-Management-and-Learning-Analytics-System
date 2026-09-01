package com.edu.questionbank.controller;

import com.edu.questionbank.dto.ApiResponse;
import com.edu.questionbank.dto.StudentVerificationDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 使用者與學生名冊比對驗證 API（直接動態適配既有資料庫，不修改使用者原有的資料庫結構）
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);
    private final JdbcTemplate jdbcTemplate;

    public UserController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** 取得所有學生清單（直接從現有資料庫資料表中抓取資料） */
    @GetMapping("/students")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getStudents() {
        List<Map<String, Object>> students = fetchExistingStudentsFromDatabase();
        return ResponseEntity.ok(ApiResponse.ok(students, students.size()));
    }

    /** 依學號或姓名查詢特定學生 */
    @GetMapping("/students/search")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchStudent(
            @RequestParam(required = false) String studentNo,
            @RequestParam(required = false) String name
    ) {
        List<Map<String, Object>> allStudents = fetchExistingStudentsFromDatabase();
        for (Map<String, Object> s : allStudents) {
            String uNo = s.get("username") != null ? String.valueOf(s.get("username")).trim() : "";
            String uName = s.get("name") != null ? String.valueOf(s.get("name")).trim() : "";

            if (studentNo != null && !studentNo.isBlank() && uNo.equalsIgnoreCase(studentNo.trim())) {
                return ResponseEntity.ok(ApiResponse.ok(s));
            }
            if (name != null && !name.isBlank() && uName.equals(name.trim())) {
                return ResponseEntity.ok(ApiResponse.ok(s));
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** 批次驗證 Excel/輸入之學生名單是否皆存在於資料庫中 */
    @PostMapping("/verify-students")
    public ResponseEntity<ApiResponse<StudentVerificationDTO.VerificationResult>> verifyStudents(
            @RequestBody List<StudentVerificationDTO.StudentItem> inputList
    ) {
        List<Map<String, Object>> dbStudents = fetchExistingStudentsFromDatabase();
        List<StudentVerificationDTO.StudentItem> resultList = new ArrayList<>();
        int verifiedCount = 0;
        int unverifiedCount = 0;

        for (StudentVerificationDTO.StudentItem item : inputList) {
            String sNo = item.getStudentNo() != null ? item.getStudentNo().trim() : "";
            String sName = item.getName() != null ? item.getName().trim() : "";
            String sEmail = item.getEmail() != null ? item.getEmail().trim() : "";

            Map<String, Object> foundUser = null;
            for (Map<String, Object> u : dbStudents) {
                String uNo = u.get("username") != null ? String.valueOf(u.get("username")).trim() : "";
                String uName = u.get("name") != null ? String.valueOf(u.get("name")).trim() : "";
                String uEmail = u.get("email") != null ? String.valueOf(u.get("email")).trim() : "";

                if (!sNo.isEmpty() && uNo.equalsIgnoreCase(sNo)) {
                    foundUser = u;
                    break;
                }
                if (!sName.isEmpty() && uName.equals(sName)) {
                    foundUser = u;
                    break;
                }
                if (!sEmail.isEmpty() && uEmail.equalsIgnoreCase(sEmail)) {
                    foundUser = u;
                    break;
                }
            }

            StudentVerificationDTO.StudentItem resItem = new StudentVerificationDTO.StudentItem();
            resItem.setStudentNo(sNo);
            resItem.setName(sName);
            resItem.setSeatNo(item.getSeatNo());
            resItem.setEmail(sEmail);
            resItem.setNote(item.getNote());

            if (foundUser != null) {
                resItem.setVerified(true);
                Object idObj = foundUser.get("id");
                if (idObj instanceof Number) {
                    resItem.setUserId(((Number) idObj).longValue());
                }
                if (foundUser.get("name") != null) resItem.setName(String.valueOf(foundUser.get("name")));
                if (foundUser.get("username") != null) resItem.setStudentNo(String.valueOf(foundUser.get("username")));
                if (foundUser.get("email") != null && !String.valueOf(foundUser.get("email")).isBlank()) {
                    resItem.setEmail(String.valueOf(foundUser.get("email")));
                }
                if (foundUser.get("seatNo") != null && !String.valueOf(foundUser.get("seatNo")).isBlank()) {
                    resItem.setSeatNo(String.valueOf(foundUser.get("seatNo")));
                }
                resItem.setStatusMessage("已在資料庫核對到學生 (" + resItem.getStudentNo() + " / " + resItem.getName() + ")");
                verifiedCount++;
            } else {
                resItem.setVerified(false);
                resItem.setStatusMessage("目前資料庫中查無此學生資料");
                unverifiedCount++;
            }

            resultList.add(resItem);
        }

        StudentVerificationDTO.VerificationResult result =
                new StudentVerificationDTO.VerificationResult(inputList.size(), verifiedCount, unverifiedCount, resultList);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 動態偵測資料庫中的使用者/學生資料表與欄位，完全不改變原有資料庫結構
     */
    private List<Map<String, Object>> fetchExistingStudentsFromDatabase() {
        List<Map<String, Object>> results = new ArrayList<>();
        try {
            // 1. 優先精準搜尋包含 FullName、GradeLevel 或 ClassName 欄位的資料表
            List<String> matchedByColumns = jdbcTemplate.queryForList(
                    "SELECT DISTINCT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME IN ('FullName', 'GradeLevel', 'ClassName', 'fullname', 'gradelevel', 'classname', 'full_name', 'grade_level', 'class_name')",
                    String.class
            );

            String targetTable = null;
            if (!matchedByColumns.isEmpty()) {
                targetTable = matchedByColumns.get(0);
                log.info("精準偵測到包含 FullName/GradeLevel 欄位之資料表: {}", targetTable);
            }

            if (targetTable == null) {
                // 取得資料庫中所有的資料表名稱
                List<String> tableNames = jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE'",
                        String.class
                );

                // 尋找可能的學生/使用者資料表（優先順序：users, user, students, student, user_info, tbl_user 等）
                for (String t : Arrays.asList("users", "user", "students", "student", "Users", "User", "Students", "Student", "user_info", "tbl_user", "accounts")) {
                    for (String realTable : tableNames) {
                        if (realTable.equalsIgnoreCase(t)) {
                            targetTable = realTable;
                            break;
                        }
                    }
                    if (targetTable != null) break;
                }

                if (targetTable == null) {
                    // 若找不到常見名稱，嘗試找包含 user 或 student 的表
                    for (String realTable : tableNames) {
                        String lower = realTable.toLowerCase();
                        if ((lower.contains("user") || lower.contains("student")) && !lower.contains("course_student") && !lower.contains("bak")) {
                            targetTable = realTable;
                            break;
                        }
                    }
                }
            }

            if (targetTable == null) {
                log.warn("資料庫中未找到使用者或學生相關資料表");
                return results;
            }

            // 2. 取得該資料表的所有欄位名稱
            List<String> columns = jdbcTemplate.queryForList(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                    String.class,
                    targetTable
            );

            // 3. 查詢所有資料列
            List<Map<String, Object>> rawRows = jdbcTemplate.queryForList("SELECT * FROM [" + targetTable + "]");

            long fallbackId = 1;
            for (Map<String, Object> row : rawRows) {
                Map<String, Object> student = new LinkedHashMap<>();

                // 智慧匹配欄位名稱（包含 GradeLevel、ClassName、FullName 等）
                Object idVal = findValueByKeywords(row, "id", "user_id", "userid", "UserId", "student_id", "studentid", "StudentId", "uid", "sn");
                Object userNoVal = findValueByKeywords(row, "StudentNo", "student_no", "studentno", "StudentId", "student_id", "UserId", "user_id", "username", "account", "user_no", "userno", "學號", "帳號", "id", "code");
                Object nameVal = findValueByKeywords(row, "FullName", "fullname", "full_name", "name", "real_name", "realname", "user_name", "username", "student_name", "cname", "姓名");
                Object emailVal = findValueByKeywords(row, "Email", "email", "mail", "信箱", "電子信箱");
                Object seatVal = findValueByKeywords(row, "SeatNumber", "seat_number", "seat_no", "seatno", "seat", "座號");
                Object roleVal = findValueByKeywords(row, "UserRole", "user_role", "role", "identity", "type", "user_type", "角色", "身份");
                Object gradeVal = findValueByKeywords(row, "GradeLevel", "gradelevel", "grade_level", "grade", "年級");
                Object classVal = findValueByKeywords(row, "ClassName", "classname", "class_name", "class_group", "classgroup", "class", "班級", "班別");

                // 如果有身分/角色欄位，且該列不是學生（例如老師、管理員），可依資料決定是否過濾
                String roleStr = roleVal != null ? String.valueOf(roleVal).trim() : "";
                if (!roleStr.isEmpty()) {
                    boolean isTeacherOrAdmin = roleStr.contains("師") || roleStr.equalsIgnoreCase("teacher") ||
                                               roleStr.contains("管") || roleStr.equalsIgnoreCase("admin");
                    if (isTeacherOrAdmin) {
                        continue; // 僅取學生
                    }
                }

                String gradeStr = gradeVal != null ? String.valueOf(gradeVal).trim() : "";
                String classStr = classVal != null ? String.valueOf(classVal).trim() : "";
                String nameStr = nameVal != null ? String.valueOf(nameVal).trim() : "";
                String userNoStr = userNoVal != null ? String.valueOf(userNoVal).trim() : "";

                // 產生班級姓名顯示格式（例如：A班王曉明，不顯示 GradeLevel）
                String displayName = "";
                if (!classStr.isEmpty() || !nameStr.isEmpty()) {
                    displayName = classStr + nameStr;
                } else if (!userNoStr.isEmpty()) {
                    displayName = userNoStr;
                } else {
                    displayName = "學生" + (idVal != null ? idVal : fallbackId);
                }

                student.put("id", idVal != null ? idVal : fallbackId++);
                student.put("username", !userNoStr.isEmpty() ? userNoStr : (!nameStr.isEmpty() ? nameStr : ("S" + student.get("id"))));
                student.put("name", !nameStr.isEmpty() ? nameStr : "學生" + student.get("id"));
                student.put("FullName", !nameStr.isEmpty() ? nameStr : student.get("name"));
                student.put("GradeLevel", gradeStr);
                student.put("grade", gradeStr);
                student.put("ClassName", classStr);
                student.put("classGroup", classStr);
                student.put("displayName", displayName);
                student.put("seatNo", seatVal != null ? String.valueOf(seatVal).trim() : "");
                student.put("email", emailVal != null ? String.valueOf(emailVal).trim() : "");
                student.put("role", roleStr.isEmpty() ? "學生" : roleStr);

                results.add(student);
            }
        } catch (Exception e) {
            log.error("動態抓取既有資料庫學生資料失敗: {}", e.getMessage(), e);
        }
        return results;
    }

    private Object findValueByKeywords(Map<String, Object> row, String... keywords) {
        for (String kw : keywords) {
            for (Map.Entry<String, Object> entry : row.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(kw)) {
                    if (entry.getValue() != null) return entry.getValue();
                }
            }
        }
        return null;
    }
}
