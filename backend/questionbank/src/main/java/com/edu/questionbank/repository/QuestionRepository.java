package com.edu.questionbank.repository;

import com.edu.questionbank.model.Question;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 題目 Repository — Spring Data JPA 自動實作 CRUD
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, Long> {

    /**
     * 依多維度條件篩選題目（空字串視為「全部」）
     */
    @Query("SELECT q FROM Question q WHERE " +
           "(:dept      IS NULL OR :dept      = '' OR q.department = :dept)      AND " +
           "(:subject   IS NULL OR :subject   = '' OR q.subject    = :subject)   AND " +
           "(:unit      IS NULL OR :unit      = '' OR q.unit       = :unit)      AND " +
           "(:difficulty IS NULL OR :difficulty = '' OR q.difficulty = :difficulty) AND " +
           "(:keyword   IS NULL OR :keyword   = '' OR q.content LIKE CONCAT('%', :keyword, '%')) " +
           "ORDER BY q.createdAt DESC")
    List<Question> findByFilters(
        @Param("dept")       String dept,
        @Param("subject")    String subject,
        @Param("unit")       String unit,
        @Param("difficulty") String difficulty,
        @Param("keyword")    String keyword
    );

    List<Question> findBySubject(String subject);
    List<Question> findByUnit(String unit);
    List<Question> findByDepartment(String department);
}
