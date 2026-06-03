package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import java.util.Date;

@Entity
@Table(name = "notes")
@Data
public class Note {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "user_id", length = 36)
    private String userId;

    @Column(name = "class_id", length = 36)
    private String classId;

    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String summary;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_at")
    private Date createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = new Date();
    }
}
