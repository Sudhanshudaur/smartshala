package com.smartshala.repository;

import com.smartshala.entity.LiveSession;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface LiveSessionRepository extends JpaRepository<LiveSession, String> {
    Optional<LiveSession> findByClassIdAndStatus(String classId, String status);
    List<LiveSession> findByStatusAndTeacherIdOrderByStartedAtDesc(String status, String teacherId);
    List<LiveSession> findByClassIdInAndStatusOrderByStartedAtDesc(List<String> classIds, String status);
    List<LiveSession> findByStatusOrderByStartedAtDesc(String status);
    long countByStatus(String status);
    void deleteByClassId(String classId);
}
