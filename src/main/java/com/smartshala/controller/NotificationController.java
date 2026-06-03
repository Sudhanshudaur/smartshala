package com.smartshala.controller;

import com.smartshala.entity.Assignment;
import com.smartshala.entity.LiveSession;
import com.smartshala.entity.StudentClass;
import com.smartshala.entity.User;
import com.smartshala.repository.AssignmentRepository;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.LiveSessionRepository;
import com.smartshala.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final StudentClassRepository studentClassRepository;
    private final ClassRepository classRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final AssignmentRepository assignmentRepository;

    @GetMapping
    public ResponseEntity<?> getNotifications(Authentication auth) {
        User user = (User) auth.getPrincipal();
        List<Map<String, Object>> notifications = new ArrayList<>();

        List<String> classIds;
        if ("teacher".equals(user.getRole())) {
            classIds = classRepository.findByTeacherIdOrderByCreatedAtDesc(user.getId()).stream()
                    .map(cls -> cls.getId())
                    .toList();
        } else if ("student".equals(user.getRole())) {
            classIds = studentClassRepository.findByStudentIdOrderByJoinedAtDesc(user.getId()).stream()
                    .map(StudentClass::getClassId)
                    .toList();
        } else {
            classIds = classRepository.findAll().stream().map(cls -> cls.getId()).toList();
        }

        if (!classIds.isEmpty()) {
            for (LiveSession session : liveSessionRepository.findByClassIdInAndStatusOrderByStartedAtDesc(classIds, "active")) {
                String className = classRepository.findById(session.getClassId()).map(cls -> cls.getName()).orElse("Class");
                notifications.add(item("live", className + " is live now", session.getStartedAt(), session.getClassId()));
            }
            for (Assignment assignment : assignmentRepository.findByClassIdInOrderByCreatedAtDesc(classIds)) {
                notifications.add(item("assignment", "Assignment: " + assignment.getTitle(), assignment.getCreatedAt(), assignment.getClassId()));
            }
        }

        return ResponseEntity.ok(notifications.stream()
                .sorted(Comparator.comparing((Map<String, Object> n) -> (Date) n.get("timestamp"), Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(12)
                .toList());
    }

    private Map<String, Object> item(String type, String message, Date timestamp, String classId) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("type", type);
        dto.put("message", message);
        dto.put("timestamp", timestamp);
        dto.put("class_id", classId);
        return dto;
    }
}
