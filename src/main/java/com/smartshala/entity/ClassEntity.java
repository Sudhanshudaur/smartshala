package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import java.util.Date;

@Entity
@Table(name = "classes")
@Data
public class ClassEntity {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "teacher_id", nullable = false)
    private String teacherId;

    @Column(unique = true, nullable = false, length = 10)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String syllabus;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = new Date();
        }
    }
}
