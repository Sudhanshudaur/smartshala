package com.smartshala.repository;

import com.smartshala.entity.Quiz;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizRepository extends JpaRepository<Quiz, String> {
    List<Quiz> findByClassIdOrderByCreatedAtDesc(String classId);
    List<Quiz> findByClassIdInOrderByCreatedAtDesc(List<String> classIds);
    long countByClassId(String classId);
    void deleteByClassId(String classId);
}
