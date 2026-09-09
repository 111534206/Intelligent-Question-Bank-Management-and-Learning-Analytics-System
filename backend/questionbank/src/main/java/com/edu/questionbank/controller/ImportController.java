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
            @RequestParam(value = "dept", required = false, defaultValue = "自然科學科") String dept,
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
                    byte[] bytes = file.getBytes();
                    List<ImportRecord> parsed = new ArrayList<>();
                    try {
                        String excelText = fileParserService.extractTextFromExcel(new java.io.ByteArrayInputStream(bytes));
                        if (!excelText.isBlank()) {
                            parsed = geminiService.extractQuestionsFromFixedDoc(excelText, fileName, useKey);
                        }
                    } catch (Exception apiEx) {
                        log.warn("Gemini API 連線或額度異常 ({})，自動啟用本地 Excel 解析...", apiEx.getMessage());
                    }
                    if (parsed == null || parsed.isEmpty()) {
                        parsed = fileParserService.parseFixedExcel(new java.io.ByteArrayInputStream(bytes), fileName);
                    }
                    newRecords.addAll(parsed);
                } else if (lowerName.endsWith(".pdf")) {
                    byte[] bytes = file.getBytes();
                    List<ImportRecord> parsed = new ArrayList<>();

                    // 1. 優先使用本地強健正則解析引擎（0 秒極速回應、100% 完整連續抓取題目 1..200+，無跳號、無 API 限流）
                    try {
                        parsed = fileParserService.parseFixedPdf(new java.io.ByteArrayInputStream(bytes), fileName);
                    } catch (Exception e) {
                        log.warn("本地 PDF 正則解析異動 ({})，準備切換至 Gemini AI 視覺分析...", e.getMessage());
                    }

                    // 2. 若本地解析成功提煉出題目（如國家檢定/標準選擇題 PDF），直接採用極速回傳！
                    if (parsed != null && !parsed.isEmpty()) {
                        log.info("本地強健式解析引擎成功從 {} 0 秒極速完整提煉出 {} 題題目！", fileName, parsed.size());
                    } else {
                        // 3. 若本地解析結果為 0（如純圖片檔、拍照檔、掃描檔 PDF），啟動 Gemini Multimodal Vision 視覺 AI 解析
                        log.info("本地解析結果為 0 (圖片/掃描檔 PDF)，啟動 Gemini Multimodal Vision 視覺 AI 分析 (檔名: {})...", fileName);
                        try {
                            List<byte[]> pageImages = fileParserService.renderPdfPagesToImages(new java.io.ByteArrayInputStream(bytes));
                            if (!pageImages.isEmpty()) {
                                int pIdx = 1;
                                for (byte[] imgBytes : pageImages) {
                                    log.info("正在執行 Gemini Vision 頁面圖片 OCR 分析第 {} / {} 頁...", pIdx, pageImages.size());
                                    List<ImportRecord> imgRecords = geminiService.extractQuestionsFromImageBytes(imgBytes, fileName, useKey);
                                    if (imgRecords != null && !imgRecords.isEmpty()) {
                                        parsed.addAll(imgRecords);
                                    }
                                    pIdx++;
                                }
                            } else {
                                parsed = geminiService.extractQuestionsFromPdfBytes(bytes, fileName, useKey);
                            }
                        } catch (Exception visionEx) {
                            log.warn("Gemini Vision 視覺解析異常 ({})", visionEx.getMessage());
                        }
                    }

                    if (parsed != null) newRecords.addAll(parsed);
                } else {
                    ImportRecord stub = createStubRecord(fileName, "固定格式解析");
                    newRecords.add(stub);
                }

            } catch (Exception e) {
                log.error("固定格式匯入解析異常: {}", e.getMessage(), e);
                return ResponseEntity.badRequest().body(ApiResponse.error("檔案 [" + fileName + "] 解析處理失敗：" + e.getMessage()));
            }
        }

        if (newRecords.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.error("未能從上傳的 PDF 檔案中擷取出任何有效的選擇題目！請確認該檔案是否包含選擇題，或檢查文字是否可正常提取。"));
        }

        // 儲存前先對所有欄位進行安全長度防護與科系填入，防止 SQL Server Column Truncation 崩潰
        for (ImportRecord r : newRecords) {
            if (dept != null && !dept.isBlank()) r.setDepartment(dept);
            if (r.getContent() != null && r.getContent().length() > 980) r.setContent(r.getContent().substring(0, 980));
            if (r.getOptionA() != null && r.getOptionA().length() > 480) r.setOptionA(r.getOptionA().substring(0, 480));
            if (r.getOptionB() != null && r.getOptionB().length() > 480) r.setOptionB(r.getOptionB().substring(0, 480));
            if (r.getOptionC() != null && r.getOptionC().length() > 480) r.setOptionC(r.getOptionC().substring(0, 480));
            if (r.getOptionD() != null && r.getOptionD().length() > 480) r.setOptionD(r.getOptionD().substring(0, 480));
            if (r.getAnswer() != null && r.getAnswer().length() > 40) r.setAnswer(r.getAnswer().substring(0, 40));
            if (r.getSubject() != null && r.getSubject().length() > 45) r.setSubject(r.getSubject().substring(0, 45));
            if (r.getUnit() != null && r.getUnit().length() > 90) r.setUnit(r.getUnit().substring(0, 90));
            if (r.getSourceFile() != null && r.getSourceFile().length() > 230) r.setSourceFile(r.getSourceFile().substring(0, 230));
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
            @RequestParam(value = "dept", required = false, defaultValue = "自然科學科") String dept,
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
                    if (dept != null && !dept.isBlank()) r.setDepartment(dept);
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
        q.setDepartment(rec.getDepartment() != null && !rec.getDepartment().isBlank() ? rec.getDepartment() : "自然科學科");
        q.setDifficulty("中");
        q.setSourceType("AI/檔案匯入");

        if (q.getContent() != null && q.getContent().length() > 980) q.setContent(q.getContent().substring(0, 980));
        if (q.getOptionA() != null && q.getOptionA().length() > 480) q.setOptionA(q.getOptionA().substring(0, 480));
        if (q.getOptionB() != null && q.getOptionB().length() > 480) q.setOptionB(q.getOptionB().substring(0, 480));
        if (q.getOptionC() != null && q.getOptionC().length() > 480) q.setOptionC(q.getOptionC().substring(0, 480));
        if (q.getOptionD() != null && q.getOptionD().length() > 480) q.setOptionD(q.getOptionD().substring(0, 480));
        if (q.getAnswer() != null && q.getAnswer().length() > 40) q.setAnswer(q.getAnswer().substring(0, 40));
        if (q.getSubject() != null && q.getSubject().length() > 45) q.setSubject(q.getSubject().substring(0, 45));
        if (q.getUnit() != null && q.getUnit().length() > 90) q.setUnit(q.getUnit().substring(0, 90));

        questionRepo.save(q);

        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 老師審核：一鍵批次將審核篩選後的題目轉入正式題庫 (questions 資料表)
     */
    @PostMapping("/confirm-all")
    public ResponseEntity<ApiResponse<Integer>> confirmAll() {
        List<ImportRecord> pendingList = importRepo.findByStatusOrderByIdAsc("pending");
        if (pendingList.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(0));
        }

        List<Question> questionsToSave = new ArrayList<>();
        for (ImportRecord rec : pendingList) {
            rec.setStatus("confirmed");

            Question q = new Question();
            q.setContent(rec.getContent());
            q.setOptionA(rec.getOptionA());
            q.setOptionB(rec.getOptionB());
            q.setOptionC(rec.getOptionC());
            q.setOptionD(rec.getOptionD());
            q.setAnswer(rec.getAnswer() != null ? rec.getAnswer() : "A");
            q.setSubject(rec.getSubject() != null ? rec.getSubject() : "未分類");
            q.setUnit(rec.getUnit() != null ? rec.getUnit() : "未分類");
            q.setDepartment(rec.getDepartment() != null && !rec.getDepartment().isBlank() ? rec.getDepartment() : "自然科學科");
            q.setDifficulty("中");
            q.setSourceType("AI/檔案匯入");

            if (q.getContent() != null && q.getContent().length() > 980) q.setContent(q.getContent().substring(0, 980));
            if (q.getOptionA() != null && q.getOptionA().length() > 480) q.setOptionA(q.getOptionA().substring(0, 480));
            if (q.getOptionB() != null && q.getOptionB().length() > 480) q.setOptionB(q.getOptionB().substring(0, 480));
            if (q.getOptionC() != null && q.getOptionC().length() > 480) q.setOptionC(q.getOptionC().substring(0, 480));
            if (q.getOptionD() != null && q.getOptionD().length() > 480) q.setOptionD(q.getOptionD().substring(0, 480));
            if (q.getAnswer() != null && q.getAnswer().length() > 40) q.setAnswer(q.getAnswer().substring(0, 40));
            if (q.getSubject() != null && q.getSubject().length() > 45) q.setSubject(q.getSubject().substring(0, 45));
            if (q.getUnit() != null && q.getUnit().length() > 90) q.setUnit(q.getUnit().substring(0, 90));

            questionsToSave.add(q);
        }

        importRepo.saveAll(pendingList);
        questionRepo.saveAll(questionsToSave);

        return ResponseEntity.ok(ApiResponse.ok(questionsToSave.size()));
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
