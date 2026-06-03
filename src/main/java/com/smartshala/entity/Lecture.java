package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import java.util.Date;

@Entity
@Table(name = "lectures")
@Data
public class Lecture {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "class_id", length = 36)
    private String classId;

    @Column(name = "teacher_id", length = 36)
    private String teacherId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Temporal(TemporalType.TIMESTAMP)
    private Date date;

    private Integer duration;
    private String subject;
    private String instructor;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = new Date();
        if (date == null) date = new Date();
    }
}
