package com.smartshala.repository;

import com.smartshala.entity.QuizAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, String> {
    Optional<QuizAttempt> findByQuizIdAndStudentId(String quizId, String studentId);
    List<QuizAttempt> findByQuizIdOrderBySubmittedAtDesc(String quizId);
    List<QuizAttempt> findByStudentIdAndQuizIdIn(String studentId, List<String> quizIds);
    void deleteByQuizId(String quizId);
    void deleteByQuizIdIn(List<String> quizIds);
    void deleteByStudentId(String studentId);
    void deleteByStudentIdAndQuizIdIn(String studentId, List<String> quizIds);
}
