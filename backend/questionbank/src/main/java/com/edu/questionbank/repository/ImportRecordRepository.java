package com.edu.questionbank.repository;

import com.edu.questionbank.model.ImportRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 匯入紀錄 Repository
 */
@Repository
public interface ImportRecordRepository extends JpaRepository<ImportRecord, Long> {

    /** 取得所有待確認的題目 (依 ID 正序排列，符合 PDF 檔案原始順序) */
    List<ImportRecord> findByStatusOrderByIdAsc(String status);

    /** 取得所有待確認的題目 (依建立時間倒序) */
    List<ImportRecord> findByStatusOrderByCreatedAtDesc(String status);

    List<ImportRecord> findBySourceFile(String sourceFile);

    /** 刪除指定來源檔案且為特定狀態的舊紀錄（防止重複上傳累積） */
    @Transactional
    void deleteBySourceFileAndStatus(String sourceFile, String status);
}

