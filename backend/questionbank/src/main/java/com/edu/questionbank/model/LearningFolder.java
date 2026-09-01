package com.edu.questionbank.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 學習資料夾實體 — 對應 learning_folders 表
 */
@Entity
@Table(name = "learning_folders")
public class LearningFolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(length = 200, nullable = false)
    private String name;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /** 此資料夾內的所有教材（OneToMany） */
    @OneToMany(mappedBy = "folder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<FolderMaterial> materials = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // ── Getters & Setters ──────────────────────────────────────

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public List<FolderMaterial> getMaterials() { return materials; }
    public void setMaterials(List<FolderMaterial> materials) { this.materials = materials; }
}
