package com.edu.questionbank.repository;

import com.edu.questionbank.model.CourseStudent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 課程修課學生 Repository
 */
@Repository
public interface CourseStudentRepository extends JpaRepository<CourseStudent, Long> {

    List<CourseStudent> findByCourseId(Long courseId);
}
