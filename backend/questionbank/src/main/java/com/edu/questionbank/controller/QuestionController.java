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
