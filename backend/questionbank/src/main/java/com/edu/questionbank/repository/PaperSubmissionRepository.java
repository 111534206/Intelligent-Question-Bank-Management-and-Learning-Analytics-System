package com.edu.questionbank.repository;

import com.edu.questionbank.model.PaperSubmission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PaperSubmissionRepository extends JpaRepository<PaperSubmission, Long> {
    List<PaperSubmission> findByPaperIdOrderByScoreDescSubmittedAtAsc(Long paperId);
    Optional<PaperSubmission> findFirstByPaperIdAndStudentNo(Long paperId, String studentNo);
    List<PaperSubmission> findByStudentNoOrderBySubmittedAtDesc(String studentNo);
}
