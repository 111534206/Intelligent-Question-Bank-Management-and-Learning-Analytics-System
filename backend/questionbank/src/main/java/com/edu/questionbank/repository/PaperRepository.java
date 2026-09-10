package com.edu.questionbank.repository;

import com.edu.questionbank.model.Paper;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface PaperRepository extends JpaRepository<Paper, Long> {
    List<Paper> findAllByOrderByCreatedAtDesc();
}
