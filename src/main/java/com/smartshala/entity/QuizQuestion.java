package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(name = "quiz_questions")
@Data
public class QuizQuestion {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "quiz_id", nullable = false, length = 36)
    private String quizId;

    @Column(name = "question_order")
    private Integer questionOrder;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "option_a", nullable = false, columnDefinition = "TEXT")
    private String optionA;

    @Column(name = "option_b", nullable = false, columnDefinition = "TEXT")
    private String optionB;

    @Column(name = "option_c", nullable = false, columnDefinition = "TEXT")
    private String optionC;

    @Column(name = "option_d", nullable = false, columnDefinition = "TEXT")
    private String optionD;

    @Column(name = "correct_option", nullable = false, length = 1)
    private String correctOption;

    @Column(columnDefinition = "TEXT")
    private String explanation;
}
