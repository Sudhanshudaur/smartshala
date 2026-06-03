package com.smartshala.controller;

import com.smartshala.entity.Assignment;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.Lecture;
import com.smartshala.entity.LiveSession;
import com.smartshala.entity.Quiz;
import com.smartshala.entity.User;
import com.smartshala.repository.AssignmentRepository;
import com.smartshala.repository.AssignmentSubmissionRepository;
import com.smartshala.repository.ChapterRepository;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.LectureRepository;
import com.smartshala.repository.LiveSessionRepository;
import com.smartshala.repository.NoteRepository;
import com.smartshala.repository.QuizAttemptRepository;
import com.smartshala.repository.QuizQuestionRepository;
import com.smartshala.repository.QuizRepository;
import com.smartshala.repository.StudentClassRepository;
import com.smartshala.repository.StudentPerformanceRepository;
import com.smartshala.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;
    private final StudentPerformanceRepository studentPerformanceRepository;
    private final LectureRepository lectureRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ChapterRepository chapterRepository;
    private final NoteRepository noteRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/users")
    public ResponseEntity<?> getUsers() {
        return ResponseEntity.ok(userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"))
                .stream()
                .map(this::userDto)
                .toList());
    }

    @PostMapping("/users")
    public ResponseEntity<?> createUser(@RequestBody UserRequest req) {
        if (req.email == null || req.email.isBlank() || req.password == null || req.password.isBlank() || req.role == null || req.role.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Name, email, password, and role are required"));
        }
        if (!isValidRole(req.role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
        }
        if (userRepository.findByEmail(req.email.trim().toLowerCase()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already in use"));
        }

        User user = new User();
        user.setName(req.name == null ? "" : req.name.trim());
        user.setEmail(req.email.trim().toLowerCase());
        user.setRole(req.role.trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(req.password));
        return ResponseEntity.ok(userDto(userRepository.save(user)));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<?> updateUser(@PathVariable String id, @RequestBody UserRequest req) {
        return userRepository.findById(id).<ResponseEntity<?>>map(user -> {
            if (req.name != null) user.setName(req.name.trim());
            if (req.email != null && !req.email.isBlank()) {
                String email = req.email.trim().toLowerCase();
                if (userRepository.findByEmail(email).filter(existing -> !existing.getId().equals(id)).isPresent()) {
                    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already in use"));
                }
                user.setEmail(email);
            }
            if (req.role != null && !req.role.isBlank()) {
                if (!isValidRole(req.role)) return ResponseEntity.badRequest().body(Map.of("error", "Invalid role"));
                user.setRole(req.role.trim().toLowerCase());
            }
            if (req.password != null && !req.password.isBlank()) {
                user.setPasswordHash(passwordEncoder.encode(req.password));
            }
            return ResponseEntity.ok(userDto(userRepository.save(user)));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/users/{id}")
    @Transactional
    public ResponseEntity<?> deleteUser(@PathVariable String id, Authentication auth) {
        User current = (User) auth.getPrincipal();
        if (current.getId().equals(id)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot delete your own admin account while logged in"));
        }

        User user = userRepository.findById(id).orElse(null);
        if (user == null) return ResponseEntity.notFound().build();

        if ("teacher".equals(user.getRole())) {
            classRepository.findByTeacherIdOrderByCreatedAtDesc(user.getId()).forEach(cls -> {
                List<String> assignmentIds = assignmentRepository.findByClassIdOrderByCreatedAtDesc(cls.getId())
                        .stream()
                        .map(Assignment::getId)
                        .toList();
                if (!assignmentIds.isEmpty()) assignmentSubmissionRepository.deleteByAssignmentIdIn(assignmentIds);
                List<String> quizIds = quizRepository.findByClassIdOrderByCreatedAtDesc(cls.getId())
                        .stream()
                        .map(Quiz::getId)
                        .toList();
                if (!quizIds.isEmpty()) {
                    quizAttemptRepository.deleteByQuizIdIn(quizIds);
                    quizQuestionRepository.deleteByQuizIdIn(quizIds);
                }
                quizRepository.deleteByClassId(cls.getId());
                assignmentRepository.deleteByClassId(cls.getId());
                chapterRepository.deleteByClassId(cls.getId());
                noteRepository.deleteByClassId(cls.getId());
                lectureRepository.deleteByClassId(cls.getId());
                liveSessionRepository.deleteByClassId(cls.getId());
                studentClassRepository.deleteByClassId(cls.getId());
                studentPerformanceRepository.deleteByClassId(cls.getId());
                classRepository.delete(cls);
            });
        } else if ("student".equals(user.getRole())) {
            studentClassRepository.deleteByStudentId(user.getId());
            studentPerformanceRepository.deleteByStudentId(user.getId());
            assignmentSubmissionRepository.deleteByStudentId(user.getId());
            quizAttemptRepository.deleteByStudentId(user.getId());
            noteRepository.deleteByUserId(user.getId());
        }

        userRepository.delete(user);
        return ResponseEntity.ok(Map.of("message", "User deleted"));
    }

    @GetMapping("/stats")
    public ResponseEntity<?> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("studentCount", userRepository.countByRole("student"));
        stats.put("teacherCount", userRepository.countByRole("teacher"));
        stats.put("adminCount", userRepository.countByRole("admin"));
        stats.put("activeClasses", classRepository.count());
        stats.put("lectureCount", lectureRepository.count());
        stats.put("assignmentCount", assignmentRepository.count());
        stats.put("quizCount", quizRepository.count());
        stats.put("activeLiveClasses", liveSessionRepository.countByStatus("active"));
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/live-classes")
    public ResponseEntity<?> getLiveClasses() {
        List<Map<String, Object>> rows = liveSessionRepository.findByStatusOrderByStartedAtDesc("active")
                .stream()
                .map(session -> {
                    ClassEntity cls = classRepository.findById(session.getClassId()).orElse(null);
                    User teacher = cls == null ? null : userRepository.findById(cls.getTeacherId()).orElse(null);
                    Map<String, Object> row = new HashMap<>();
                    row.put("id", session.getRoomId());
                    row.put("sessionId", session.getId());
                    row.put("classId", session.getClassId());
                    row.put("className", cls == null ? "Class" : cls.getName());
                    row.put("teacher", Map.of("name", teacher == null ? "Teacher" : teacher.getName()));
                    row.put("studentCount", session.getClassId() == null ? 0 : studentClassRepository.countByClassId(session.getClassId()));
                    row.put("startTime", session.getStartedAt());
                    return row;
                })
                .toList();
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/activity")
    public ResponseEntity<?> getActivity() {
        List<Map<String, Object>> activity = new ArrayList<>();

        for (User user : userRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream().limit(8).toList()) {
            activity.add(activityItem(user.getCreatedAt(), user.getName() + " joined as " + user.getRole()));
        }
        for (Lecture lecture : lectureRepository.findByOrderByCreatedAtDesc().stream().limit(8).toList()) {
            activity.add(activityItem(lecture.getCreatedAt(), "Lecture uploaded: " + lecture.getTitle()));
        }
        for (LiveSession session : liveSessionRepository.findAll().stream()
                .sorted(Comparator.comparing(LiveSession::getStartedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(8)
                .toList()) {
            activity.add(activityItem(session.getStartedAt(), "Live class " + session.getStatus()));
        }
        for (Assignment assignment : assignmentRepository.findAll().stream()
                .sorted(Comparator.comparing(Assignment::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(8)
                .toList()) {
            activity.add(activityItem(assignment.getCreatedAt(), "Assignment created: " + assignment.getTitle()));
        }
        for (Quiz quiz : quizRepository.findAll().stream()
                .sorted(Comparator.comparing(Quiz::getCreatedAt, Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(8)
                .toList()) {
            activity.add(activityItem(quiz.getCreatedAt(), "AI quiz generated: " + quiz.getTitle()));
        }

        return ResponseEntity.ok(activity.stream()
                .filter(item -> item.get("timestamp") != null)
                .sorted(Comparator.comparing((Map<String, Object> item) -> (Date) item.get("timestamp"), Comparator.nullsLast(Date::compareTo)).reversed())
                .limit(20)
                .toList());
    }

    private Map<String, Object> userDto(User user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", user.getId());
        dto.put("name", user.getName());
        dto.put("email", user.getEmail());
        dto.put("role", user.getRole());
        dto.put("createdAt", user.getCreatedAt());
        return dto;
    }

    private Map<String, Object> activityItem(Date timestamp, String message) {
        Map<String, Object> item = new HashMap<>();
        item.put("timestamp", timestamp);
        item.put("message", message);
        return item;
    }

    private boolean isValidRole(String role) {
        String normalized = role.trim().toLowerCase();
        return "student".equals(normalized) || "teacher".equals(normalized) || "admin".equals(normalized);
    }

    @Data
    static class UserRequest {
        String name;
        String email;
        String password;
        String role;
    }
}
