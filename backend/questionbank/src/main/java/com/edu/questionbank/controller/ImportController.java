package com.edu.questionbank.controller;

import com.edu.questionbank.dto.ApiResponse;
import com.edu.questionbank.model.ImportRecord;
import com.edu.questionbank.model.Question;
import com.edu.questionbank.repository.ImportRecordRepository;
import com.edu.questionbank.repository.QuestionRepository;
import com.edu.questionbank.service.FileParserService;
import com.edu.questionbank.service.GeminiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 題庫匯入 REST Controller
 *
 * 1. POST /api/import/fixed   — 抓取 PDF / Excel 檔案內的【原題目、選項與答案】（Gemini API 驅動 + 本地解析備援）
 * 2. POST /api/import/ppt-ai  — 分析 PPT 簡報內文，由 Gemini API 【自動創作與生成題目】
 *
 * 審核與篩選端點：
 * GET    /api/import/pending      — 取得待審核題目清單（讓老師進行線上篩選與修改）
 * PUT    /api/import/{id}/confirm  — 確認單筆並轉入正式題庫 (questions 表)
 * POST   /api/import/confirm-all  — 批次一鍵確認所有篩選後的題目轉入正式題庫
 * PUT    /api/import/{id}         — 修改/編輯待確認題目內容與選項
 * DELETE /api/import/{id}         — 刪除/剔除不合適的題目
 */
@RestController
@RequestMapping("/api/import")
@CrossOrigin(origins = "*")
@Transactional
public class ImportController {

    private static final Logger log = LoggerFactory.getLogger(ImportController.class);

    private final ImportRecordRepository importRepo;
    private final QuestionRepository     questionRepo;
    private final FileParserService      fileParserService;
    private final GeminiService          geminiService;

    public ImportController(ImportRecordRepository importRepo,
                            QuestionRepository questionRepo,
                            FileParserService fileParserService,
                            GeminiService geminiService) {
        this.importRepo        = importRepo;
        this.questionRepo      = questionRepo;
        this.fileParserService = fileParserService;
        this.geminiService     = geminiService;
    }

    /**
     * 模式 1：抓取 PDF / Excel 檔案中的【原題目、選項與答案】
     */
    @PostMapping("/fixed")
    public ResponseEntity<ApiResponse<List<ImportRecord>>> importFixedFormat(
            @RequestParam("files") List<MultipartFile> files,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String apiKeyHeader,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam
    ) {
        String useKey = (apiKeyHeader != null && !apiKeyHeader.isBlank()) ? apiKeyHeader : apiKeyParam;
        List<ImportRecord> newRecords = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
            try {
                String lowerName = fileName.toLowerCase();
                if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                    List<ImportRecord> parsed = fileParserService.parseFixedExcel(file.getInputStream(), fileName);
                    newRecords.addAll(parsed);
                } else if (lowerName.endsWith(".pdf")) {
                    byte[] bytes = file.getBytes();
                    List<ImportRecord> parsed = new ArrayList<>();

                    // 1. 若代入 Gemini API Key，優先依 PDF 頁數進行【分頁批次擷取 (Page Chunking)】，徹底突破 8192 Token 限制
                    if (useKey != null && !useKey.isBlank()) {
                        List<String> pages = fileParserService.extractTextPagesFromPdf(new java.io.ByteArrayInputStream(bytes));
                        if (!pages.isEmpty()) {
                            parsed = geminiService.extractQuestionsFromPdfPages(pages, fileName, useKey);
                        } else {
                            String pdfText = fileParserService.extractTextFromPdf(new java.io.ByteArrayInputStream(bytes));
                            parsed = geminiService.extractQuestionsFromFixedDoc(pdfText, fileName, useKey);
                        }
                    }

                    // 2. 若未使用 API Key 或 API 回傳為空，使用全方位技能檢定正則與真實段落提煉
                    if (parsed.isEmpty()) {
                        parsed = fileParserService.parseFixedPdf(new java.io.ByteArrayInputStream(bytes), fileName);
                    }

                    newRecords.addAll(parsed);
                } else {
                    ImportRecord stub = createStubRecord(fileName, "固定格式解析");
                    newRecords.add(stub);
                }

            } catch (Exception e) {
                log.error("固定格式匯入解析異常: {}", e.getMessage(), e);
                return ResponseEntity.badRequest().body(ApiResponse.error("檔案 [" + fileName + "] 解析處理失敗：" + e.getMessage()));
            }
        }

        // 儲存前先清除同一來源檔名的舊 pending 記錄，防止重複上傳時題目重複累積
        for (MultipartFile file : files) {
            String fn = file.getOriginalFilename();
            if (fn != null && !fn.isBlank()) {
                importRepo.deleteBySourceFileAndStatus(fn, "pending");
            }
        }

        importRepo.saveAll(newRecords);
        List<ImportRecord> pending = importRepo.findByStatusOrderByIdAsc("pending");
        return ResponseEntity.ok(ApiResponse.ok(pending, pending.size()));
    }

    /**
     * 模式 2：分析 PPT 簡報內文，由 Gemini API 【自動創作與生成題目】
     */
    @PostMapping("/ppt-ai")
    public ResponseEntity<ApiResponse<List<ImportRecord>>> importPptAi(
            @RequestParam("files") List<MultipartFile> files,
            @RequestParam(value = "subject", required = false, defaultValue = "一般") String subject,
            @RequestParam(value = "difficulty", required = false, defaultValue = "中") String difficulty,
            @RequestParam(value = "count", required = false, defaultValue = "5") Integer count,
            @RequestHeader(value = "X-Gemini-Api-Key", required = false) String apiKeyHeader,
            @RequestParam(value = "apiKey", required = false) String apiKeyParam
    ) {
        String useKey = (apiKeyHeader != null && !apiKeyHeader.isBlank()) ? apiKeyHeader : apiKeyParam;
        List<ImportRecord> newRecords = new ArrayList<>();

        for (MultipartFile file : files) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file.pptx";
            try {
                String pptText = fileParserService.extractTextFromPpt(file.getInputStream(), fileName);
                List<ImportRecord> aiGenerated = geminiService.generateQuestionsFromPpt(pptText, fileName, useKey, count != null ? count : 5);
                for (ImportRecord r : aiGenerated) {
                    r.setSubject(subject);
                }
                newRecords.addAll(aiGenerated);
            } catch (Exception e) {
                log.error("PPT AI 智慧出題異常: {}", e.getMessage(), e);
                return ResponseEntity.badRequest().body(ApiResponse.error("PPT 簡報 [" + fileName + "] AI 出題失敗：" + e.getMessage()));
            }
        }

        // 儲存前先清除同一來源檔名的舊 pending 記錄，防止重複上傳時題目重複累積
        for (MultipartFile file : files) {
            String fn = file.getOriginalFilename();
            if (fn != null && !fn.isBlank()) {
                importRepo.deleteBySourceFileAndStatus(fn, "pending");
            }
        }

        importRepo.saveAll(newRecords);
        List<ImportRecord> pending = importRepo.findByStatusOrderByIdAsc("pending");
        return ResponseEntity.ok(ApiResponse.ok(pending, pending.size()));
    }

    /**
     * 取得目前待確認審核的題目清單 (供老師線上篩選、編輯與剔除)
     */
    @GetMapping("/pending")
    public ResponseEntity<ApiResponse<List<ImportRecord>>> getPendingList() {
        List<ImportRecord> pending = importRepo.findByStatusOrderByIdAsc("pending");
        return ResponseEntity.ok(ApiResponse.ok(pending, pending.size()));
    }

    /**
     * 老師審核：單筆確認題目並轉入正式題庫 (questions 資料表)
     */
    @PutMapping("/{id}/confirm")
    public ResponseEntity<ApiResponse<Void>> confirm(@PathVariable Long id) {
        Optional<ImportRecord> opt = importRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        ImportRecord rec = opt.get();
        rec.setStatus("confirmed");
        importRepo.save(rec);

        Question q = new Question();
        q.setContent(rec.getContent());
        q.setOptionA(rec.getOptionA());
        q.setOptionB(rec.getOptionB());
        q.setOptionC(rec.getOptionC());
        q.setOptionD(rec.getOptionD());
        q.setAnswer(rec.getAnswer() != null ? rec.getAnswer() : "A");
        q.setSubject(rec.getSubject() != null ? rec.getSubject() : "未分類");
        q.setUnit(rec.getUnit() != null ? rec.getUnit() : "未分類");
        q.setDepartment("自然科學科");
        q.setDifficulty("中");
        q.setSourceType("AI/檔案匯入");
        questionRepo.save(q);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 老師審核：一鍵批次將審核篩選後的題目轉入正式題庫 (questions 資料表)
     */
    @PostMapping("/confirm-all")
    public ResponseEntity<ApiResponse<Integer>> confirmAll() {
        List<ImportRecord> pendingList = importRepo.findByStatusOrderByIdAsc("pending");
        int count = 0;
        for (ImportRecord rec : pendingList) {
            rec.setStatus("confirmed");
            importRepo.save(rec);

            Question q = new Question();
            q.setContent(rec.getContent());
            q.setOptionA(rec.getOptionA());
            q.setOptionB(rec.getOptionB());
            q.setOptionC(rec.getOptionC());
            q.setOptionD(rec.getOptionD());
            q.setAnswer(rec.getAnswer() != null ? rec.getAnswer() : "A");
            q.setSubject(rec.getSubject() != null ? rec.getSubject() : "未分類");
            q.setUnit(rec.getUnit() != null ? rec.getUnit() : "未分類");
            q.setDepartment("自然科學科");
            q.setDifficulty("中");
            q.setSourceType("AI/檔案匯入");
            questionRepo.save(q);
            count++;
        }
        return ResponseEntity.ok(ApiResponse.ok(count));
    }

    /**
     * 老師修改待確認題目
     */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ImportRecord>> update(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body
    ) {
        Optional<ImportRecord> opt = importRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        ImportRecord rec = opt.get();
        if (body.containsKey("content"))  rec.setContent((String) body.get("content"));
        if (body.containsKey("optA"))     rec.setOptionA((String) body.get("optA"));
        if (body.containsKey("optB"))     rec.setOptionB((String) body.get("optB"));
        if (body.containsKey("optC"))     rec.setOptionC((String) body.get("optC"));
        if (body.containsKey("optD"))     rec.setOptionD((String) body.get("optD"));
        if (body.containsKey("answer"))   rec.setAnswer((String) body.get("answer"));
        if (body.containsKey("subject"))  rec.setSubject((String) body.get("subject"));
        if (body.containsKey("unit"))     rec.setUnit((String) body.get("unit"));
        importRepo.save(rec);

        return ResponseEntity.ok(ApiResponse.ok(rec));
    }

    /**
     * 老師剔除/刪除待確認題目
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!importRepo.existsById(id)) return ResponseEntity.notFound().build();
        importRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    private ImportRecord createStubRecord(String fileName, String sourceLabel) {
        ImportRecord r = new ImportRecord();
        r.setContent("（來自 " + fileName + "）根據檔案內容，此題目之正確選項為何？");
        r.setOptionA("符合原文描述之正確選項");
        r.setOptionB("次要補充觀念與延伸說明");
        r.setOptionC("錯誤之對應說明");
        r.setOptionD("未在內文中呈現之描述");
        r.setAnswer("A");
        r.setSourceFile(fileName);
        r.setConfidence(90);
        r.setStatus("pending");
        r.setSubject("綜合科目");
        r.setUnit("匯入單元");
        return r;
    }
}
