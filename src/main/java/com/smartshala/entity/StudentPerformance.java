package com.smartshala.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;
import java.util.Date;

@Entity
@Table(name = "student_performance")
@Data
public class StudentPerformance {
    @Id
    @UuidGenerator
    @Column(length = 36)
    private String id;

    @Column(name = "student_id", nullable = false, length = 36)
    private String studentId;

    @Column(name = "class_id", nullable = false, length = 36)
    private String classId;

    @Column(columnDefinition = "INT DEFAULT 0")
    private Integer score = 0;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "last_updated")
    private Date lastUpdated;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        lastUpdated = new Date();
    }
}
