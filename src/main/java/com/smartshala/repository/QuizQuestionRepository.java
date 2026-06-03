package com.smartshala.repository;

import com.smartshala.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, String> {
    List<QuizQuestion> findByQuizIdOrderByQuestionOrderAsc(String quizId);
    void deleteByQuizId(String quizId);
    void deleteByQuizIdIn(List<String> quizIds);
}
