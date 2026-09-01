package com.edu.questionbank.repository;

import com.edu.questionbank.model.LearningFolder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * 學習資料夾 Repository
 */
@Repository
public interface LearningFolderRepository extends JpaRepository<LearningFolder, Long> {
    // 繼承 JpaRepository 已提供 findAll / findById / save / deleteById
}
