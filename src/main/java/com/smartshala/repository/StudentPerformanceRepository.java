package com.smartshala.repository;

import com.smartshala.entity.StudentPerformance;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentPerformanceRepository extends JpaRepository<StudentPerformance, String> {
    Optional<StudentPerformance> findByStudentIdAndClassId(String studentId, String classId);
    List<StudentPerformance> findByClassIdOrderByScoreDesc(String classId);
    List<StudentPerformance> findByStudentId(String studentId);
    void deleteByClassId(String classId);
    void deleteByStudentId(String studentId);
}
