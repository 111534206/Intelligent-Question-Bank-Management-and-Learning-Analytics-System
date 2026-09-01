package com.edu.questionbank.controller;

import com.edu.questionbank.dto.ApiResponse;
import com.edu.questionbank.model.FolderMaterial;
import com.edu.questionbank.model.LearningFolder;
import com.edu.questionbank.model.Question;
import com.edu.questionbank.repository.LearningFolderRepository;
import com.edu.questionbank.repository.QuestionRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

/**
 * 學習資料夾 API
 *
 * GET    /api/folders                          — 取得所有資料夾
 * POST   /api/folders                          — 建立新資料夾
 * DELETE /api/folders/{id}                     — 刪除資料夾
 * GET    /api/folders/{id}/materials           — 取得資料夾教材
 * POST   /api/folders/{id}/materials           — 上傳教材（multipart）
 * DELETE /api/folders/{id}/materials/{matId}   — 移除教材
 * POST   /api/folders/{id}/generate            — 觸發 AI 統整題生成（Stub）
 * POST   /api/folders/{id}/questions/{qid}/import — 將統整題加入題庫
 */
@RestController
@RequestMapping("/api/folders")
public class FolderController {

    private final LearningFolderRepository folderRepo;
    private final QuestionRepository       questionRepo;

    public FolderController(LearningFolderRepository folderRepo,
                             QuestionRepository questionRepo) {
        this.folderRepo   = folderRepo;
        this.questionRepo = questionRepo;
    }

    /** 取得所有資料夾 */
    @GetMapping
    public ResponseEntity<ApiResponse<List<LearningFolder>>> listFolders() {
        List<LearningFolder> folders = folderRepo.findAll();
        return ResponseEntity.ok(ApiResponse.ok(folders, folders.size()));
    }

    /** 建立新資料夾 */
    @PostMapping
    public ResponseEntity<ApiResponse<LearningFolder>> createFolder(
            @RequestBody Map<String, String> body
    ) {
        String name = body.getOrDefault("name", "新資料夾");
        LearningFolder folder = new LearningFolder();
        folder.setName(name);
        LearningFolder saved = folderRepo.save(folder);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /** 刪除資料夾（含教材 — ON DELETE CASCADE） */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteFolder(@PathVariable Long id) {
        if (!folderRepo.existsById(id)) return ResponseEntity.notFound().build();
        folderRepo.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /** 取得資料夾內教材清單 */
    @GetMapping("/{id}/materials")
    public ResponseEntity<ApiResponse<List<FolderMaterial>>> getMaterials(
            @PathVariable Long id
    ) {
        Optional<LearningFolder> opt = folderRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.ok(opt.get().getMaterials()));
    }

    /**
     * 上傳教材到資料夾
     *
     * 接通實際儲存時：
     *   1. 儲存至 uploads/folders/{folderId}/
     *   2. 記錄 file_path 到 folder_materials
     */
    @PostMapping("/{id}/materials")
    public ResponseEntity<ApiResponse<FolderMaterial>> uploadMaterial(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file
    ) {
        Optional<LearningFolder> opt = folderRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        LearningFolder folder = opt.get();
        String fileName = file.getOriginalFilename() != null
                ? file.getOriginalFilename() : "unknown";

        String ext = "";
        if (fileName.contains(".")) ext = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase();
        String fileType = switch (ext) {
            case "pdf"  -> "PDF";
            case "pptx", "ppt" -> "PPT";
            case "docx", "doc" -> "DOC";
            case "xlsx", "xls" -> "Excel";
            case "png", "jpg", "jpeg" -> "圖片";
            default -> "其他";
        };

        FolderMaterial mat = new FolderMaterial();
        mat.setFolder(folder);
        mat.setFileName(fileName);
        mat.setFileType(fileType);
        // mat.setFilePath(savedPath.toString()); // 接通儲存後設定

        folder.getMaterials().add(mat);
        folderRepo.save(folder);

        return ResponseEntity.ok(ApiResponse.ok(mat));
    }

    /** 移除教材 */
    @DeleteMapping("/{id}/materials/{matId}")
    public ResponseEntity<ApiResponse<Void>> removeMaterial(
            @PathVariable Long id,
            @PathVariable Long matId
    ) {
        Optional<LearningFolder> opt = folderRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        LearningFolder folder = opt.get();
        folder.getMaterials().removeIf(m -> m.getId().equals(matId));
        folderRepo.save(folder);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    /**
     * 觸發 AI 統整題生成 — Stub 實作
     *
     * 接通 LLM 服務時：
     *   1. 讀取資料夾內所有教材（解析 PDF/PPT 文字）
     *   2. 呼叫 OpenAI / Azure OpenAI API 生成跨章節題目
     *   3. 回傳結構化 JSON
     *
     * 目前回傳 3 道預設跨章節題目。
     */
    @PostMapping("/{id}/generate")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> generate(
            @PathVariable Long id
    ) {
        Optional<LearningFolder> opt = folderRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        LearningFolder folder = opt.get();
        if (folder.getMaterials().size() < 2) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("至少需要 2 份教材才能生成統整題"));
        }

        // Stub 統整題（實際應由 AI 生成）
        List<Map<String, Object>> questions = List.of(
            Map.of("gid","g1",
                "content", "綜合本資料夾各教材：在均勻電場中做等加速度直線運動的帶電粒子，其電場力 F 與加速度 a 的關係符合下列哪項？",
                "optA","F 與 a 成正比（F=ma），方向相同",
                "optB","F 與 a 成反比，方向相反",
                "optC","F 與 a 無關",
                "optD","F 只與電場強度有關",
                "answer","A",
                "chapter","力學 × 電磁學",
                "subject","物理",
                "added", false),
            Map.of("gid","g2",
                "content", "根據本資料夾教材的交叉比對：光速 c 與電場常數 ε₀ 及磁場常數 μ₀ 的關係為？",
                "optA","c = 1/√(ε₀μ₀)",
                "optB","c = ε₀ × μ₀",
                "optC","c = √(ε₀/μ₀)",
                "optD","c = ε₀ + μ₀",
                "answer","A",
                "chapter","電磁學 × 波動光學",
                "subject","物理",
                "added", false),
            Map.of("gid","g3",
                "content", "下列哪項敘述同時正確描述了力學與波動的共同特性？",
                "optA","兩者均服從能量守恆定律",
                "optB","力學中動量不守恆",
                "optC","只有力學有干涉現象",
                "optD","波動不具備動量",
                "answer","A",
                "chapter","力學 × 波動光學",
                "subject","物理",
                "added", false)
        );

        return ResponseEntity.ok(ApiResponse.ok(questions, questions.size()));
    }

    /**
     * 將統整題加入題庫
     */
    @PostMapping("/{id}/questions/{gid}/import")
    public ResponseEntity<ApiResponse<Question>> importGenerated(
            @PathVariable Long id,
            @PathVariable String gid,
            @RequestBody Map<String, Object> body
    ) {
        Question q = new Question();
        q.setContent((String) body.getOrDefault("content", ""));
        q.setOptionA((String) body.getOrDefault("optA", ""));
        q.setOptionB((String) body.getOrDefault("optB", ""));
        q.setOptionC((String) body.getOrDefault("optC", ""));
        q.setOptionD((String) body.getOrDefault("optD", ""));
        q.setAnswer((String) body.getOrDefault("answer", "A"));
        q.setSubject((String) body.getOrDefault("subject", "物理"));
        q.setUnit((String) body.getOrDefault("chapter", "跨章節統整"));
        q.setDepartment("自然科學科");
        q.setDifficulty("難");
        q.setSourceType("AI統整");
        Question saved = questionRepo.save(q);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }
}
