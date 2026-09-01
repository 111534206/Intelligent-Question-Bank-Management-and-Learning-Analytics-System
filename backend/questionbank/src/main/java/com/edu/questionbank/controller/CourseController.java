package com.edu.questionbank.controller;

import com.edu.questionbank.dto.ApiResponse;
import com.edu.questionbank.dto.CourseDTO;
import com.edu.questionbank.dto.CourseStudentDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.*;

/**
 * 課程資料管理 API（動態適配既有資料庫 dbo.Courses 與 course_students 資料表）
 */
@RestController
@RequestMapping("/api/courses")
public class CourseController {

    private static final Logger log = LoggerFactory.getLogger(CourseController.class);
    private final JdbcTemplate jdbcTemplate;

    public CourseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        initStudentTableIfNeeded();
    }

    /** 確保 course_students 資料表存在（用於關聯修課學生與小老師） */
    private void initStudentTableIfNeeded() {
        try {
            List<String> tables = jdbcTemplate.queryForList(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('course_students', 'CourseStudents')",
                    String.class
            );
            if (tables.isEmpty()) {
                log.info("建立 course_students 關聯表...");
                jdbcTemplate.execute(
                        "CREATE TABLE course_students (" +
                        "    id BIGINT IDENTITY(1,1) PRIMARY KEY," +
                        "    course_id BIGINT NOT NULL," +
                        "    student_no NVARCHAR(50) NOT NULL," +
                        "    name NVARCHAR(100) NOT NULL," +
                        "    seat_no NVARCHAR(20)," +
                        "    email NVARCHAR(150)," +
                        "    note NVARCHAR(255)," +
                        "    is_assistant BIT DEFAULT 0," +
                        "    assistant_permissions NVARCHAR(500)," +
                        "    created_at DATETIME2 DEFAULT GETDATE()" +
                        ")"
                );
            }
        } catch (Exception e) {
            log.warn("檢查或建立 course_students 表時狀況: {}", e.getMessage());
        }
    }

    /** 動態偵測 Courses 資料表名稱 */
    private String getCoursesTableName() {
        List<String> tables = jdbcTemplate.queryForList(
                "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE='BASE TABLE' AND TABLE_NAME IN ('Courses', 'courses', 'tbl_courses', 'course', 'Course')",
                String.class
        );
        return !tables.isEmpty() ? tables.get(0) : "Courses";
    }

    /** 取得所有課程列表（含學生人數與名單） */
    @GetMapping
    public ResponseEntity<ApiResponse<List<CourseDTO>>> listCourses() {
        String tableName = getCoursesTableName();
        List<Map<String, Object>> rows = new ArrayList<>();
        try {
            rows = jdbcTemplate.queryForList("SELECT * FROM [" + tableName + "] ORDER BY 1 DESC");
        } catch (Exception e) {
            log.warn("讀取課程列表失敗: {}", e.getMessage());
        }

        List<CourseDTO> dtos = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            CourseDTO dto = mapRowToDTO(row);
            if (dto.getId() != null) {
                dto.setStudents(getStudentsForCourse(dto.getId()));
            }
            dtos.add(dto);
        }
        return ResponseEntity.ok(ApiResponse.ok(dtos, dtos.size()));
    }

    /** 取得特定課程詳情 */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CourseDTO>> getCourse(@PathVariable Long id) {
        String tableName = getCoursesTableName();
        String idCol = findIdColumnName(tableName);
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    "SELECT * FROM [" + tableName + "] WHERE [" + idCol + "] = ?", id
            );
            if (!rows.isEmpty()) {
                CourseDTO dto = mapRowToDTO(rows.get(0));
                dto.setStudents(getStudentsForCourse(id));
                return ResponseEntity.ok(ApiResponse.ok(dto));
            }
        } catch (Exception e) {
            log.warn("讀取課程 {} 失敗: {}", id, e.getMessage());
        }
        return ResponseEntity.notFound().build();
    }

    /** 新增課程（直接寫入 dbo.Courses 與修課學生名冊） */
    @PostMapping
    @Transactional
    public ResponseEntity<ApiResponse<CourseDTO>> createCourse(@RequestBody CourseDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("課程名稱不可為空"));
        }

        String tableName = getCoursesTableName();
        List<String> actualCols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                String.class,
                tableName
        );

        // 智慧解析欄位映射
        Map<String, Object> insertMap = new LinkedHashMap<>();

        // 課程名稱 (CourseName)
        putIfMatch(insertMap, actualCols, "CourseName", dto.getName().trim(), "coursename", "name", "course_name", "title");

        // 授課教師 (TeacherID / TeacherName)
        Object teacherVal = resolveTeacherValue(actualCols, dto.getTeacher());
        if (teacherVal != null) {
            String col = findActualCol(actualCols, "TeacherID", "teacherid", "teacher_id", "teacher", "teacher_name");
            if (col != null) insertMap.put(col, teacherVal);
        }

        // 科目 (SubjectID)
        String subjCol = findActualCol(actualCols, "SubjectID", "subjectid", "subject_id", "subject");
        if (subjCol != null) {
            insertMap.put(subjCol, 1);
        }

        // 狀態 (Status)
        String statusCol = findActualCol(actualCols, "Status", "status", "state");
        if (statusCol != null) {
            insertMap.put(statusCol, "啟用中");
        }

        // 建立時間 (CreatedAt)
        String createdCol = findActualCol(actualCols, "CreatedAt", "createdat", "created_at", "create_time");
        if (createdCol != null) {
            insertMap.put(createdCol, new Timestamp(System.currentTimeMillis()));
        }

        // 其他擴充欄位（若使用者的表中恰好包含）
        putIfMatch(insertMap, actualCols, "Code", dto.getCode() != null ? dto.getCode() : "CRS-" + (System.currentTimeMillis() % 1000000), "code", "course_code");
        putIfMatch(insertMap, actualCols, "Grade", dto.getGrade() != null ? dto.getGrade() : "高二", "grade", "gradelevel", "grade_level");
        putIfMatch(insertMap, actualCols, "ClassGroup", dto.getClassGroup() != null ? dto.getClassGroup() : "忠班", "classgroup", "class_group", "classname", "class_name");
        putIfMatch(insertMap, actualCols, "Type", dto.getType() != null ? dto.getType() : "必修", "type", "course_type");
        putIfMatch(insertMap, actualCols, "Year", dto.getYear() != null ? dto.getYear() : "113", "year", "academic_year");
        putIfMatch(insertMap, actualCols, "Semester", dto.getSemester() != null ? dto.getSemester() : "1", "semester");
        putIfMatch(insertMap, actualCols, "Credits", dto.getCredits() != null ? dto.getCredits() : "3", "credits");

        // 組合動態 SQL INSERT
        StringBuilder colNames = new StringBuilder();
        StringBuilder placeholders = new StringBuilder();
        List<Object> params = new ArrayList<>();

        for (Map.Entry<String, Object> entry : insertMap.entrySet()) {
            if (colNames.length() > 0) {
                colNames.append(", ");
                placeholders.append(", ");
            }
            colNames.append("[").append(entry.getKey()).append("]");
            placeholders.append("?");
            params.add(entry.getValue());
        }

        String sql = "INSERT INTO [" + tableName + "] (" + colNames + ") VALUES (" + placeholders + ")";
        log.info("執行課程寫入 SQL: {} (參數: {})", sql, params);

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        Long newCourseId = key != null ? key.longValue() : System.currentTimeMillis();

        // 寫入學生名冊與小老師資訊
        if (dto.getStudents() != null && !dto.getStudents().isEmpty()) {
            saveStudentsForCourse(newCourseId, dto.getStudents());
        }

        dto.setId(newCourseId);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** 更新課程（含修課學生名冊） */
    @PutMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<CourseDTO>> updateCourse(
            @PathVariable Long id,
            @RequestBody CourseDTO dto
    ) {
        String tableName = getCoursesTableName();
        String idCol = findIdColumnName(tableName);
        List<String> actualCols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                String.class,
                tableName
        );

        StringBuilder setClause = new StringBuilder();
        List<Object> params = new ArrayList<>();

        String nameCol = findActualCol(actualCols, "CourseName", "coursename", "name", "course_name", "title");
        if (nameCol != null && dto.getName() != null && !dto.getName().isBlank()) {
            setClause.append("[").append(nameCol).append("] = ?, ");
            params.add(dto.getName().trim());
        }

        String teacherCol = findActualCol(actualCols, "TeacherID", "teacherid", "teacher_id", "teacher", "teacher_name");
        if (teacherCol != null && dto.getTeacher() != null) {
            Object teacherVal = resolveTeacherValue(actualCols, dto.getTeacher());
            setClause.append("[").append(teacherCol).append("] = ?, ");
            params.add(teacherVal);
        }

        if (setClause.length() > 0) {
            setClause.setLength(setClause.length() - 2);
            params.add(id);
            String sql = "UPDATE [" + tableName + "] SET " + setClause + " WHERE [" + idCol + "] = ?";
            jdbcTemplate.update(sql, params.toArray());
        }

        if (dto.getStudents() != null) {
            try {
                jdbcTemplate.update("DELETE FROM course_students WHERE course_id = ?", id);
            } catch (Exception ignored) {}
            saveStudentsForCourse(id, dto.getStudents());
        }

        dto.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /** 刪除課程（連帶刪除修課學生） */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteCourse(@PathVariable Long id) {
        String tableName = getCoursesTableName();
        String idCol = findIdColumnName(tableName);
        try {
            jdbcTemplate.update("DELETE FROM course_students WHERE course_id = ?", id);
            jdbcTemplate.update("DELETE FROM [" + tableName + "] WHERE [" + idCol + "] = ?", id);
            return ResponseEntity.ok(ApiResponse.ok(null));
        } catch (Exception e) {
            log.error("刪除課程失敗: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error("刪除失敗: " + e.getMessage()));
        }
    }

    /** 取得特定課程的學生名單 */
    @GetMapping("/{id}/students")
    public ResponseEntity<ApiResponse<List<CourseStudentDTO>>> getCourseStudents(@PathVariable Long id) {
        List<CourseStudentDTO> dtos = getStudentsForCourse(id);
        return ResponseEntity.ok(ApiResponse.ok(dtos, dtos.size()));
    }

    private List<CourseStudentDTO> getStudentsForCourse(Long courseId) {
        try {
            return jdbcTemplate.query(
                    "SELECT * FROM course_students WHERE course_id = ? ORDER BY id ASC",
                    (rs, rowNum) -> new CourseStudentDTO(
                            rs.getLong("id"),
                            rs.getString("student_no"),
                            rs.getString("name"),
                            rs.getString("seat_no"),
                            rs.getString("email"),
                            rs.getString("note"),
                            rs.getBoolean("is_assistant"),
                            rs.getString("assistant_permissions")
                    ),
                    courseId
            );
        } catch (Exception e) {
            log.warn("讀取課程 {} 學生名單失敗: {}", courseId, e.getMessage());
            return new ArrayList<>();
        }
    }

    private void saveStudentsForCourse(Long courseId, List<CourseStudentDTO> students) {
        for (CourseStudentDTO s : students) {
            try {
                jdbcTemplate.update(
                        "INSERT INTO course_students (course_id, student_no, name, seat_no, email, note, is_assistant, assistant_permissions) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                        courseId,
                        s.getStudentNo() != null ? s.getStudentNo() : "",
                        s.getName() != null ? s.getName() : "未命名",
                        s.getSeatNo() != null ? s.getSeatNo() : "",
                        s.getEmail() != null ? s.getEmail() : "",
                        s.getNote() != null ? s.getNote() : "",
                        s.getIsAssistant() != null && s.getIsAssistant() ? 1 : 0,
                        s.getAssistantPermissions() != null ? s.getAssistantPermissions() : ""
                );
            } catch (Exception e) {
                log.warn("寫入 course_students 學生 {} 失敗: {}", s.getName(), e.getMessage());
            }

            // 同步寫入原有的 dbo.CourseStudents (CourseID, StudentID, JoinDate)
            try {
                List<String> csTables = jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('CourseStudents', 'coursestudents', 'tbl_course_students')",
                        String.class
                );
                if (!csTables.isEmpty()) {
                    String csTable = csTables.get(0);
                    Object studentId = findStudentDbId(s.getStudentNo(), s.getName());
                    if (studentId != null) {
                        jdbcTemplate.update(
                                "IF NOT EXISTS (SELECT 1 FROM [" + csTable + "] WHERE CourseID = ? AND StudentID = ?) " +
                                "INSERT INTO [" + csTable + "] (CourseID, StudentID, JoinDate) VALUES (?, ?, GETDATE())",
                                courseId, studentId, courseId, studentId
                        );
                    }
                }
            } catch (Exception e) {
                log.debug("同步 CourseStudents 紀錄: {}", e.getMessage());
            }
        }
    }

    private Object findStudentDbId(String studentNo, String studentName) {
        try {
            // 優先找包含 FullName 或 StudentNo 的使用者/學生表
            List<String> matchedTables = jdbcTemplate.queryForList(
                    "SELECT DISTINCT TABLE_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE COLUMN_NAME IN ('FullName', 'GradeLevel', 'ClassName', 'StudentID', 'UserID')",
                    String.class
            );

            if (matchedTables.isEmpty()) {
                matchedTables = jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('Users', 'users', 'Students', 'students', 'tbl_user')",
                        String.class
                );
            }

            for (String uTable : matchedTables) {
                if (uTable.equalsIgnoreCase("course_students") || uTable.equalsIgnoreCase("CourseStudents")) continue;
                List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM [" + uTable + "]");
                for (Map<String, Object> r : rows) {
                    String uNo = String.valueOf(findValue(r, "username", "StudentNo", "student_no", "StudentID", "account", "code", "學號", "帳號"));
                    String uName = String.valueOf(findValue(r, "FullName", "fullname", "name", "姓名"));
                    
                    boolean noMatch = studentNo != null && !studentNo.isBlank() && uNo.equalsIgnoreCase(studentNo.trim());
                    boolean nameMatch = studentName != null && !studentName.isBlank() && uName.equals(studentName.trim());

                    if (noMatch || nameMatch) {
                        Object id = findValue(r, "StudentID", "UserID", "user_id", "id", "UserId");
                        if (id != null) return id;
                    }
                }
            }

            // 若學號本身為純數字，直接當作 StudentID
            if (studentNo != null && studentNo.matches("^\\d+$")) {
                return Long.parseLong(studentNo);
            }
        } catch (Exception e) {
            log.warn("尋找 StudentID 發生異常: {}", e.getMessage());
        }
        return null;
    }

    private String findIdColumnName(String tableName) {
        List<String> cols = jdbcTemplate.queryForList(
                "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = ?",
                String.class,
                tableName
        );
        for (String c : cols) {
            if (c.equalsIgnoreCase("CourseID") || c.equalsIgnoreCase("course_id") || c.equalsIgnoreCase("id")) {
                return c;
            }
        }
        return cols.isEmpty() ? "CourseID" : cols.get(0);
    }

    private CourseDTO mapRowToDTO(Map<String, Object> row) {
        CourseDTO dto = new CourseDTO();
        Object idVal = findValue(row, "CourseID", "course_id", "id", "CourseId");
        if (idVal instanceof Number) dto.setId(((Number) idVal).longValue());
        else if (idVal != null) {
            try { dto.setId(Long.parseLong(String.valueOf(idVal))); } catch (Exception ignored) {}
        }

        Object nameVal = findValue(row, "CourseName", "coursename", "name", "course_name", "title");
        dto.setName(nameVal != null ? String.valueOf(nameVal) : "未命名課程");

        Object teacherVal = findValue(row, "TeacherID", "teacher_id", "teacher", "teacher_name", "TeacherName");
        dto.setTeacher(teacherVal != null ? String.valueOf(teacherVal) : "王大明");

        Object gradeVal = findValue(row, "Grade", "grade_level", "gradelevel", "GradeLevel");
        dto.setGrade(gradeVal != null ? String.valueOf(gradeVal) : "高二");

        Object classVal = findValue(row, "ClassGroup", "class_group", "classname", "ClassName", "Class");
        dto.setClassGroup(classVal != null ? String.valueOf(classVal) : "忠班");

        Object codeVal = findValue(row, "Code", "code", "course_code", "coursecode");
        dto.setCode(codeVal != null ? String.valueOf(codeVal) : (dto.getId() != null ? "CRS-" + dto.getId() : "CRS-001"));

        Object statusVal = findValue(row, "Status", "status");
        dto.setStatus(statusVal != null ? String.valueOf(statusVal) : "啟用中");

        Object createdVal = findValue(row, "CreatedAt", "created_at", "createdat");
        if (createdVal != null) dto.setCreatedAt(String.valueOf(createdVal).split(" ")[0]);

        return dto;
    }

    private Object resolveTeacherValue(List<String> actualCols, String teacherName) {
        String teacherCol = findActualCol(actualCols, "TeacherID", "teacherid", "teacher_id");
        if (teacherCol != null) {
            try {
                List<String> uTables = jdbcTemplate.queryForList(
                        "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_NAME IN ('Users', 'users', 'tbl_user')",
                        String.class
                );
                if (!uTables.isEmpty()) {
                    String uTable = uTables.get(0);
                    List<Map<String, Object>> teacherRows = jdbcTemplate.queryForList(
                            "SELECT TOP 1 * FROM [" + uTable + "] WHERE (FullName LIKE ? OR username LIKE ?)",
                            "%" + (teacherName != null ? teacherName : "") + "%",
                            "%" + (teacherName != null ? teacherName : "") + "%"
                    );
                    if (!teacherRows.isEmpty()) {
                        Object tId = findValue(teacherRows.get(0), "UserID", "user_id", "id", "UserId");
                        if (tId != null) return tId;
                    }
                }
            } catch (Exception ignored) {}
            return 1;
        }
        return teacherName != null ? teacherName : "王大明";
    }

    private void putIfMatch(Map<String, Object> map, List<String> actualCols, String preferredName, Object value, String... keywords) {
        String col = findActualCol(actualCols, preferredName, keywords);
        if (col != null && value != null) {
            map.put(col, value);
        }
    }

    private String findActualCol(List<String> actualCols, String preferredName, String... keywords) {
        for (String c : actualCols) {
            if (c.equalsIgnoreCase(preferredName)) return c;
        }
        for (String kw : keywords) {
            for (String c : actualCols) {
                if (c.equalsIgnoreCase(kw)) return c;
            }
        }
        return null;
    }

    private Object findValue(Map<String, Object> map, String... keys) {
        for (String k : keys) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(k)) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
