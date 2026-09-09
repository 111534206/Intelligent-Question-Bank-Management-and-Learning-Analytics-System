package com.edu.questionbank.controller;

import com.edu.questionbank.dto.ApiResponse;
import com.edu.questionbank.dto.QuestionDTO;
import com.edu.questionbank.model.Question;
import com.edu.questionbank.repository.QuestionRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * 題庫管理 API
 *
 * GET  /api/questions              — 取得題目清單（多維篩選）
 * POST /api/questions              — 新增題目
 * PUT  /api/questions/{id}         — 更新題目
 * DELETE /api/questions/{id}       — 刪除題目
 */
@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private final QuestionRepository questionRepo;

    public QuestionController(QuestionRepository questionRepo) {
        this.questionRepo = questionRepo;
    }

    /**
     * 取得題目清單，支援多維篩選與關鍵字搜尋
     *
     * @param dept       科系（可選）
     * @param subject    科目（可選）
     * @param unit       單元（可選）
     * @param difficulty 難度 易/中/難（可選）
     * @param keyword    關鍵字（可選）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Question>>> list(
            @RequestParam(defaultValue = "") String dept,
            @RequestParam(defaultValue = "") String subject,
            @RequestParam(defaultValue = "") String unit,
            @RequestParam(defaultValue = "") String difficulty,
            @RequestParam(defaultValue = "") String keyword
    ) {
        List<Question> questions = questionRepo.findByFilters(dept, subject, unit, difficulty, keyword);
        return ResponseEntity.ok(ApiResponse.ok(questions, questions.size()));
    }

    /**
     * 新增題目
     */
    @PostMapping
    public ResponseEntity<ApiResponse<Question>> create(@Valid @RequestBody QuestionDTO dto) {
        Question q = new Question();
        mapDtoToEntity(dto, q);
        Question saved = questionRepo.save(q);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /**
     * 更新題目
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Question>> update(
            @PathVariable Long id,
            @Valid @RequestBody QuestionDTO dto
    ) {
        Optional<Question> opt = questionRepo.findById(id);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Question q = opt.get();
        mapDtoToEntity(dto, q);
        Question saved = questionRepo.save(q);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /**
     * 刪除題目
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!questionRepo.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        questionRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 批次修改科系、科目或單元名稱（支援整組轉換歸屬科系/科目）
     */
    @PutMapping("/batch-rename")
    public ResponseEntity<ApiResponse<Integer>> batchRename(@RequestBody java.util.Map<String, String> body) {
        String type = body.get("type"); // "dept", "subject", "unit"
        String oldName = body.get("oldName");
        String newName = body.get("newName");
        String parentDept = body.get("dept");
        String parentSubj = body.get("subject");

        String targetDept = body.get("targetDept");
        String targetSubj = body.get("targetSubj");
        String targetUnit = body.get("targetUnit");

        List<Question> questions = questionRepo.findAll();
        int count = 0;
        for (Question q : questions) {
            boolean match = false;
            if ("dept".equalsIgnoreCase(type)) {
                if (oldName != null && oldName.equalsIgnoreCase(q.getDepartment())) {
                    match = true;
                }
            } else if ("subject".equalsIgnoreCase(type)) {
                if (oldName != null && oldName.equalsIgnoreCase(q.getSubject()) && 
                   (parentDept == null || parentDept.isBlank() || parentDept.equalsIgnoreCase(q.getDepartment()))) {
                    match = true;
                }
            } else if ("unit".equalsIgnoreCase(type)) {
                if (oldName != null && oldName.equalsIgnoreCase(q.getUnit()) && 
                   (parentSubj == null || parentSubj.isBlank() || parentSubj.equalsIgnoreCase(q.getSubject())) &&
                   (parentDept == null || parentDept.isBlank() || parentDept.equalsIgnoreCase(q.getDepartment()))) {
                    match = true;
                }
            }

            if (match) {
                if (newName != null && !newName.isBlank()) {
                    if ("dept".equalsIgnoreCase(type)) q.setDepartment(newName);
                    else if ("subject".equalsIgnoreCase(type)) q.setSubject(newName);
                    else if ("unit".equalsIgnoreCase(type)) q.setUnit(newName);
                }
                if (targetDept != null && !targetDept.isBlank()) {
                    q.setDepartment(targetDept);
                }
                if (targetSubj != null && !targetSubj.isBlank()) {
                    q.setSubject(targetSubj);
                }
                if (targetUnit != null && !targetUnit.isBlank()) {
                    q.setUnit(targetUnit);
                }
                questionRepo.save(q);
                count++;
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    /**
     * 批次修改多筆指定ID試題的科系、科目、單元
     */
    @PostMapping("/batch-update-dept")
    public ResponseEntity<ApiResponse<Integer>> batchUpdateSelectedQuestions(@RequestBody java.util.Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<Integer> ids = (List<Integer>) body.get("ids");
        String targetDept = (String) body.get("targetDept");
        String targetSubj = (String) body.get("targetSubj");
        String targetUnit = (String) body.get("targetUnit");

        if (ids == null || ids.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("未選取任何試題"));
        }

        int count = 0;
        for (Integer id : ids) {
            Optional<Question> opt = questionRepo.findById(id.longValue());
            if (opt.isPresent()) {
                Question q = opt.get();
                if (targetDept != null && !targetDept.isBlank()) q.setDepartment(targetDept);
                if (targetSubj != null && !targetSubj.isBlank()) q.setSubject(targetSubj);
                if (targetUnit != null && !targetUnit.isBlank()) q.setUnit(targetUnit);
                questionRepo.save(q);
                count++;
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    /**
     * 複製某個單元的所有題目到目標科系/科目下（產生新的題目ID）
     */
    @PostMapping("/batch-copy-unit")
    public ResponseEntity<ApiResponse<Integer>> batchCopyUnit(@RequestBody java.util.Map<String, String> body) {
        String unitName = body.get("unitName");
        String sourceDept = body.get("sourceDept");
        String sourceSubj = body.get("sourceSubj");
        String targetDept = body.get("targetDept");
        String targetSubj = body.get("targetSubj");

        if (unitName == null || unitName.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("單元名稱不可空白"));
        }

        List<Question> allQuestions = questionRepo.findAll();
        int count = 0;
        for (Question src : allQuestions) {
            boolean match = unitName.equalsIgnoreCase(src.getUnit())
                    && (sourceDept == null || sourceDept.isBlank() || sourceDept.equalsIgnoreCase(src.getDepartment()))
                    && (sourceSubj == null || sourceSubj.isBlank() || sourceSubj.equalsIgnoreCase(src.getSubject()));
            if (match) {
                Question copy = new Question();
                copy.setContent(src.getContent());
                copy.setOptionA(src.getOptionA());
                copy.setOptionB(src.getOptionB());
                copy.setOptionC(src.getOptionC());
                copy.setOptionD(src.getOptionD());
                copy.setAnswer(src.getAnswer());
                copy.setDifficulty(src.getDifficulty());
                copy.setSourceType(src.getSourceType());
                copy.setUnit(src.getUnit());
                copy.setDepartment(targetDept != null && !targetDept.isBlank() ? targetDept : src.getDepartment());
                copy.setSubject(targetSubj != null && !targetSubj.isBlank() ? targetSubj : src.getSubject());
                questionRepo.save(copy);
                count++;
            }
        }
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    // ── 私有輔助方法 ───────────────────────────────────────────

    private void mapDtoToEntity(QuestionDTO dto, Question q) {
        q.setContent(dto.getContent());
        q.setOptionA(dto.getOptA());
        q.setOptionB(dto.getOptB());
        q.setOptionC(dto.getOptC());
        q.setOptionD(dto.getOptD());
        q.setAnswer(dto.getAnswer());
        q.setSubject(dto.getSubject() != null ? dto.getSubject() : "未分類");
        q.setUnit(dto.getUnit() != null ? dto.getUnit() : "未分類");
        q.setDepartment(dto.getDept() != null ? dto.getDept() : "自然科學科");
        q.setDifficulty(dto.getDifficulty() != null ? dto.getDifficulty() : "中");
        q.setSourceType(dto.getSource() != null ? dto.getSource() : "教師手動");
    }
}
