package com.smartshala.controller;

import com.smartshala.entity.LiveSession;
import com.smartshala.entity.User;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.LiveSessionRepository;
import com.smartshala.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class LiveSessionController {

    private final LiveSessionRepository liveSessionRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;

    @GetMapping("/live-sessions/active")
    public ResponseEntity<List<LiveSession>> getActiveLiveSessions() {
        return ResponseEntity.ok(liveSessionRepository.findByStatusOrderByStartedAtDesc("active"));
    }

    @GetMapping("/classes/{classId}/live")
    public ResponseEntity<?> getLiveSessionStatus(@PathVariable String classId, Authentication auth) {
        User user = (User) auth.getPrincipal();
        if (!canViewClass(user, classId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot view this live class"));
        }
        return liveSessionRepository.findByClassIdAndStatus(classId, "active")
                .<ResponseEntity<?>>map(session -> ResponseEntity.ok(Map.of("active", true, "session", session)))
                .orElseGet(() -> ResponseEntity.ok(Map.of("active", false)));
    }

    @PostMapping("/classes/{classId}/live/start")
    public ResponseEntity<?> startLiveSession(@PathVariable String classId, Authentication auth) {
        User user = (User) auth.getPrincipal();
        if (!canManageClass(user, classId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can start a live class"));
        }
        LiveSession session = liveSessionRepository.findByClassIdAndStatus(classId, "active")
                .orElseGet(() -> {
                    LiveSession newSession = new LiveSession();
                    newSession.setClassId(classId);
                    newSession.setTeacherId(user.getId());
                    newSession.setRoomId(java.util.UUID.randomUUID().toString());
                    newSession.setStatus("active");
                    return liveSessionRepository.save(newSession);
                });
        return ResponseEntity.ok(session);
    }

    @PostMapping("/classes/{classId}/live/end")
    public ResponseEntity<?> endLiveSession(@PathVariable String classId, Authentication auth) {
        User user = (User) auth.getPrincipal();
        if (!canManageClass(user, classId)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can end a live class"));
        }
        liveSessionRepository.findByClassIdAndStatus(classId, "active").ifPresent(session -> {
            session.setStatus("ended");
            session.setEndedAt(new java.util.Date());
            liveSessionRepository.save(session);
        });
        return ResponseEntity.ok(Map.of("message", "Session ended"));
    }

    private boolean canManageClass(User user, String classId) {
        return classRepository.findById(classId)
                .map(cls -> "admin".equals(user.getRole()) || user.getId().equals(cls.getTeacherId()))
                .orElse(false);
    }

    private boolean canViewClass(User user, String classId) {
        return canManageClass(user, classId)
                || studentClassRepository.findByStudentIdAndClassId(user.getId(), classId).isPresent();
    }
}
