package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;

@Entity
@Table(name = "quizzes")
@Data
public class Quiz {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "class_id", nullable = false, length = 36)
    private String classId;

    @Column(name = "teacher_id", nullable = false, length = 36)
    private String teacherId;

    @Column(nullable = false)
    private String title;

    @Column(name = "source_title")
    private String sourceTitle;

    @Column(name = "max_score")
    private Integer maxScore = 50;

    @Column(name = "question_count")
    private Integer questionCount = 5;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = new Date();
        if (maxScore == null) maxScore = 50;
        if (questionCount == null) questionCount = 5;
    }
}
