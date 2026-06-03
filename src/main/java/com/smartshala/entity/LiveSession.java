package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import java.util.Date;

@Entity
@Table(name = "live_sessions")
@Data
public class LiveSession {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "class_id", length = 36)
    private String classId;

    @Column(name = "teacher_id", length = 36)
    private String teacherId;

    private String title;

    @Column(name = "room_id", unique = true, length = 100)
    private String roomId;

    private String status; // 'active', 'ended'

    @Column(name = "recording_url", columnDefinition = "TEXT")
    private String recordingUrl;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "started_at")
    private Date startedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "ended_at")
    private Date endedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) startedAt = new Date();
        if (status == null) status = "active";
    }
}
