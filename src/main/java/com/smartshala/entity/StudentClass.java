package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import java.util.Date;

@Entity
@Table(name = "student_classes")
@Data
public class StudentClass {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    @Column(name = "class_id", nullable = false, length = 36)
    private String classId;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "joined_at")
    private Date joinedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = new Date();
    }
}
