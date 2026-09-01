package com.edu.questionbank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 資料夾教材實體 — 對應 folder_materials 表
 */
@Entity
@Table(name = "folder_materials")
public class FolderMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id", nullable = false)
    private LearningFolder folder;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "file_type", length = 20)
    private String fileType;  // PDF / PPT / DOC / Excel / 圖片

    @Column(name = "file_path", length = 500)
    private String filePath;  // 伺服器儲存路徑（實際串接時使用）

    @Column(name = "uploaded_at")
    private LocalDateTime uploadedAt;

    @PrePersist
    protected void onCreate() {
        uploadedAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LearningFolder getFolder() { return folder; }
    public void setFolder(LearningFolder folder) { this.folder = folder; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getFileType() { return fileType; }
    public void setFileType(String fileType) { this.fileType = fileType; }
    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }
    public LocalDateTime getUploadedAt() { return uploadedAt; }
}
