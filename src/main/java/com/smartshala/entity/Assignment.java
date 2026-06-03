package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;

@Entity
@Table(name = "assignments")
@Data
public class Assignment {
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

    @Column(columnDefinition = "TEXT")
    private String description;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "due_at")
    private Date dueAt;

    @Column(name = "max_score")
    private Integer maxScore = 100;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = new Date();
        if (maxScore == null) maxScore = 100;
    }
}
