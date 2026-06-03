package com.smartshala.repository;

import com.smartshala.entity.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentRepository extends JpaRepository<Assignment, String> {
    List<Assignment> findByClassIdOrderByCreatedAtDesc(String classId);
    List<Assignment> findByClassIdInOrderByCreatedAtDesc(List<String> classIds);
    long countByClassId(String classId);
    void deleteByClassId(String classId);
}
