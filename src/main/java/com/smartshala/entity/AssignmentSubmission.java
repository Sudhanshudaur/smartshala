package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.util.Date;

@Entity
@Table(name = "assignment_submissions")
@Data
public class AssignmentSubmission {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "assignment_id", nullable = false, length = 36)
    private String assignmentId;

    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    @Column(name = "file_url", columnDefinition = "TEXT")
    private String fileUrl;

    @Column(name = "file_name")
    private String fileName;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String feedback;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "submitted_at")
    private Date submittedAt;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "graded_at")
    private Date gradedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) submittedAt = new Date();
    }
}
