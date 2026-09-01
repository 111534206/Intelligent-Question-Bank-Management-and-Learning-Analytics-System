package com.edu.questionbank.repository;

import com.edu.questionbank.model.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * 課程 Repository
 */
@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {

    @Query("SELECT DISTINCT c FROM Course c LEFT JOIN FETCH c.students ORDER BY c.id DESC")
    List<Course> findAllWithStudents();
}
