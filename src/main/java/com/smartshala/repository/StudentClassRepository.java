package com.smartshala.repository;

import com.smartshala.entity.StudentClass;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface StudentClassRepository extends JpaRepository<StudentClass, String> {
    List<StudentClass> findByStudentIdOrderByJoinedAtDesc(String studentId);
    List<StudentClass> findByClassId(String classId);
    Optional<StudentClass> findByStudentIdAndClassId(String studentId, String classId);
    long countByClassId(String classId);
    void deleteByStudentIdAndClassId(String studentId, String classId);
    void deleteByClassId(String classId);
    void deleteByStudentId(String studentId);
}
