package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;

@Entity
@Table(
        name = "quiz_attempts",
        uniqueConstraints = @UniqueConstraint(columnNames = {"quiz_id", "student_id"})
)
@Data
public class QuizAttempt {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "quiz_id", nullable = false, length = 36)
    private String quizId;

    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    private Integer score = 0;

    @Column(name = "max_score")
    private Integer maxScore = 0;

    @Column(name = "correct_count")
    private Integer correctCount = 0;

    @Column(name = "total_questions")
    private Integer totalQuestions = 0;

    @Column(name = "answers_json", columnDefinition = "TEXT")
    private String answersJson;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "submitted_at")
    private Date submittedAt;

    @PrePersist
    @PreUpdate
    protected void onSubmit() {
        submittedAt = new Date();
    }
}
