package com.edu.questionbank.controller;

import com.edu.questionbank.dto.ApiResponse;
import com.edu.questionbank.dto.PaperDTO;
import com.edu.questionbank.dto.PaperResultDTO;
import com.edu.questionbank.dto.PaperSubmissionDTO;
import com.edu.questionbank.dto.QuestionDTO;
import com.edu.questionbank.model.Paper;
import com.edu.questionbank.model.PaperSubmission;
import com.edu.questionbank.model.Question;
import com.edu.questionbank.repository.PaperRepository;
import com.edu.questionbank.repository.PaperSubmissionRepository;
import com.edu.questionbank.repository.QuestionRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/papers")
@CrossOrigin(origins = "*")
public class PaperController {

    @Autowired
    private PaperRepository paperRepository;

    @Autowired
    private PaperSubmissionRepository paperSubmissionRepository;

    @Autowired
    private QuestionRepository questionRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 取得試卷清單（可依學生班級與角色過濾）
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<PaperDTO>>> listPapers(
            @RequestParam(required = false) String studentClass,
            @RequestParam(required = false) String role) {

        List<Paper> papers = paperRepository.findAllByOrderByCreatedAtDesc();
        LocalDateTime now = LocalDateTime.now();

        List<PaperDTO> dtoList = papers.stream().map(p -> {
            PaperDTO dto = toDTO(p);
            // 判斷狀態
            dto.setStatus(determineStatus(p, now));
            return dto;
        }).filter(dto -> {
            // 如果是學生端查詢，過濾開放對象
            if ("student".equalsIgnoreCase(role) && studentClass != null && !studentClass.isBlank()) {
                String target = dto.getTargetAudience();
                if (target != null && !target.equalsIgnoreCase("ALL") && !target.contains("全部")) {
                    List<String> targetClasses = Arrays.asList(target.split("[,、 ]+"));
                    boolean match = targetClasses.stream().anyMatch(c -> c.trim().equalsIgnoreCase(studentClass.trim()));
                    if (!match) return false;
                }
            }
            return true;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.ok(dtoList, dtoList.size()));
    }

    /**
     * 取得單一試卷詳情與題目
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PaperDTO>> getPaper(@PathVariable Long id) {
        return paperRepository.findById(id).map(p -> {
            PaperDTO dto = toDTO(p);
            dto.setStatus(determineStatus(p, LocalDateTime.now()));
            dto.setQuestions(loadQuestions(p.getQuestionIds()));
            return ResponseEntity.ok(ApiResponse.ok(dto));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 建立固定試卷
     */
    @PostMapping
    public ResponseEntity<ApiResponse<PaperDTO>> createPaper(@RequestBody PaperDTO req) {
        if (req.getName() == null || req.getName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("試卷名稱不可為空"));
        }

        Paper paper = new Paper();
        paper.setName(req.getName().trim());
        paper.setSubject(req.getSubject());
        paper.setDepartment(req.getDepartment());
        paper.setStartTime(req.getStartTime());
        paper.setEndTime(req.getEndTime());
        paper.setTargetAudience(req.getTargetAudience() != null ? req.getTargetAudience() : "ALL");
        paper.setShowScoreImmediately(req.getShowScoreImmediately() != null ? req.getShowScoreImmediately() : true);
        paper.setShowAnswerImmediately(req.getShowAnswerImmediately() != null ? req.getShowAnswerImmediately() : true);
        paper.setShowRankImmediately(req.getShowRankImmediately() != null ? req.getShowRankImmediately() : true);
        paper.setQuestionIds(req.getQuestionIds());

        // 計算題數
        if (req.getQuestionIds() != null && !req.getQuestionIds().isBlank()) {
            String[] ids = req.getQuestionIds().split("[,、 ]+");
            paper.setQuestionCount(ids.length);
        } else {
            paper.setQuestionCount(0);
        }

        paper.setDurationMinutes(req.getDurationMinutes() != null ? req.getDurationMinutes() : 30);
        paper.setCreatedBy(req.getCreatedBy() != null ? req.getCreatedBy() : "教師");

        Paper saved = paperRepository.save(paper);
        PaperDTO dto = toDTO(saved);
        dto.setStatus(determineStatus(saved, LocalDateTime.now()));
        return ResponseEntity.ok(ApiResponse.ok(dto));
    }

    /**
     * 修改試卷
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PaperDTO>> updatePaper(@PathVariable Long id, @RequestBody PaperDTO req) {
        return paperRepository.findById(id).map(paper -> {
            if (req.getName() != null && !req.getName().isBlank()) paper.setName(req.getName().trim());
            if (req.getSubject() != null) paper.setSubject(req.getSubject());
            if (req.getDepartment() != null) paper.setDepartment(req.getDepartment());
            paper.setStartTime(req.getStartTime());
            paper.setEndTime(req.getEndTime());
            if (req.getTargetAudience() != null) paper.setTargetAudience(req.getTargetAudience());
            if (req.getShowScoreImmediately() != null) paper.setShowScoreImmediately(req.getShowScoreImmediately());
            if (req.getShowAnswerImmediately() != null) paper.setShowAnswerImmediately(req.getShowAnswerImmediately());
            if (req.getShowRankImmediately() != null) paper.setShowRankImmediately(req.getShowRankImmediately());
            if (req.getQuestionIds() != null) {
                paper.setQuestionIds(req.getQuestionIds());
                String[] ids = req.getQuestionIds().split("[,、 ]+");
                paper.setQuestionCount(ids.length);
            }
            if (req.getDurationMinutes() != null) paper.setDurationMinutes(req.getDurationMinutes());

            Paper saved = paperRepository.save(paper);
            PaperDTO dto = toDTO(saved);
            dto.setStatus(determineStatus(saved, LocalDateTime.now()));
            return ResponseEntity.ok(ApiResponse.ok(dto));
        }).orElse(ResponseEntity.notFound().build());
    }

    /**
     * 刪除試卷
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deletePaper(@PathVariable Long id) {
        if (paperRepository.existsById(id)) {
            paperRepository.deleteById(id);
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        return ResponseEntity.notFound().build();
    }

    /**
     * 學生繳交試卷並結算成績與排名
     */
    @PostMapping("/{id}/submit")
    public ResponseEntity<ApiResponse<PaperResultDTO>> submitPaper(
            @PathVariable Long id,
            @RequestBody PaperSubmissionDTO req) {

        Optional<Paper> paperOpt = paperRepository.findById(id);
        if (paperOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Paper paper = paperOpt.get();
        List<QuestionDTO> questions = loadQuestions(paper.getQuestionIds());

        int totalCount = questions.size();
        int correctCount = 0;
        Map<Long, String> studentAnswers = req.getAnswers() != null ? req.getAnswers() : new HashMap<>();
        List<PaperResultDTO.QuestionReviewItem> reviews = new ArrayList<>();

        for (QuestionDTO q : questions) {
            String stdAns = studentAnswers.get(q.getId());
            if (stdAns == null) stdAns = "";
            String correctAns = q.getAnswer() != null ? q.getAnswer().trim() : "A";
            boolean isCorrect = correctAns.equalsIgnoreCase(stdAns.trim());
            if (isCorrect) correctCount++;

            PaperResultDTO.QuestionReviewItem item = new PaperResultDTO.QuestionReviewItem();
            item.setQuestionId(q.getId());
            item.setContent(q.getContent());
            item.setOptA(q.getOptA());
            item.setOptB(q.getOptB());
            item.setOptC(q.getOptC());
            item.setOptD(q.getOptD());
            item.setStudentAnswer(stdAns);
            item.setCorrectAnswer(correctAns);
            item.setIsCorrect(isCorrect);
            reviews.add(item);
        }

        int score = totalCount > 0 ? (int) Math.round((double) correctCount / totalCount * 100) : 0;

        // 儲存繳交紀錄
        PaperSubmission submission = new PaperSubmission();
        submission.setPaperId(id);
        submission.setStudentNo(req.getStudentNo() != null ? req.getStudentNo() : "S1130001");
        submission.setStudentName(req.getStudentName() != null ? req.getStudentName() : "學生");
        submission.setStudentClass(req.getStudentClass() != null ? req.getStudentClass() : "忠班");
        submission.setScore(score);
        submission.setTotalQuestions(totalCount);
        submission.setCorrectCount(correctCount);
        try {
            submission.setAnswersJson(objectMapper.writeValueAsString(studentAnswers));
        } catch (Exception e) {
            submission.setAnswersJson("{}");
        }

        PaperSubmission savedSub = paperSubmissionRepository.save(submission);

        // 計算該試卷目前繳卷學生排名
        List<PaperSubmission> allSubs = paperSubmissionRepository.findByPaperIdOrderByScoreDescSubmittedAtAsc(id);
        int rank = 1;
        for (int i = 0; i < allSubs.size(); i++) {
            if (allSubs.get(i).getId().equals(savedSub.getId())) {
                rank = i + 1;
                break;
            }
        }
        int totalSubs = allSubs.size();
        String percentile = totalSubs > 1 ? String.format("擊敗 %.0f%% 同學", (double) (totalSubs - rank) / (totalSubs - 1) * 100) : "目前第 1 名";

        // 組裝回傳結果（依據三大開關配置）
        PaperResultDTO result = new PaperResultDTO();
        result.setSubmissionId(savedSub.getId());
        result.setPaperId(id);
        result.setPaperName(paper.getName());
        result.setShowScore(paper.getShowScoreImmediately());
        result.setShowAnswer(paper.getShowAnswerImmediately());
        result.setShowRank(paper.getShowRankImmediately());

        result.setTotalQuestions(totalCount);
        result.setCorrectCount(correctCount);
        result.setScore(score);
        result.setAccuracy(totalCount > 0 ? (double) correctCount / totalCount : 0.0);

        result.setRank(rank);
        result.setTotalStudentsInAudience(totalSubs);
        result.setRankPercentile(percentile);
        result.setQuestionReviews(reviews);

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    // ── Helper Methods ──────────────────────────────────────────

    private String determineStatus(Paper p, LocalDateTime now) {
        if (p.getStartTime() != null && now.isBefore(p.getStartTime())) {
            return "UPCOMING"; // 未開始
        }
        if (p.getEndTime() != null && now.isAfter(p.getEndTime())) {
            return "ENDED"; // 已截止
        }
        return "ACTIVE"; // 進行中 (開放作答)
    }

    private PaperDTO toDTO(Paper p) {
        PaperDTO dto = new PaperDTO();
        dto.setId(p.getId());
        dto.setName(p.getName());
        dto.setSubject(p.getSubject());
        dto.setDepartment(p.getDepartment());
        dto.setStartTime(p.getStartTime());
        dto.setEndTime(p.getEndTime());
        dto.setTargetAudience(p.getTargetAudience());
        dto.setShowScoreImmediately(p.getShowScoreImmediately());
        dto.setShowAnswerImmediately(p.getShowAnswerImmediately());
        dto.setShowRankImmediately(p.getShowRankImmediately());
        dto.setQuestionIds(p.getQuestionIds());
        dto.setQuestionCount(p.getQuestionCount());
        dto.setDurationMinutes(p.getDurationMinutes());
        dto.setCreatedBy(p.getCreatedBy());
        dto.setCreatedAt(p.getCreatedAt());
        return dto;
    }

    private List<QuestionDTO> loadQuestions(String questionIds) {
        if (questionIds == null || questionIds.isBlank()) return Collections.emptyList();
        List<Long> ids = Arrays.stream(questionIds.split("[,、 ]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> {
                    try { return Long.parseLong(s); } catch (Exception e) { return null; }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (ids.isEmpty()) return Collections.emptyList();

        List<Question> questions = questionRepository.findAllById(ids);
        // 按原本 ids 的順序排序
        Map<Long, Question> map = questions.stream().collect(Collectors.toMap(Question::getId, q -> q));
        return ids.stream().map(map::get).filter(Objects::nonNull).map(q -> {
            QuestionDTO dto = new QuestionDTO();
            dto.setId(q.getId());
            dto.setContent(q.getContent());
            dto.setOptA(q.getOptionA());
            dto.setOptB(q.getOptionB());
            dto.setOptC(q.getOptionC());
            dto.setOptD(q.getOptionD());
            dto.setAnswer(q.getAnswer());
            dto.setSubject(q.getSubject());
            dto.setUnit(q.getUnit());
            dto.setDept(q.getDepartment());
            dto.setDifficulty(q.getDifficulty());
            dto.setSource(q.getSourceType());
            return dto;
        }).collect(Collectors.toList());
    }
}
