package com.smartshala.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.Quiz;
import com.smartshala.entity.StudentClass;
import com.smartshala.entity.StudentPerformance;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/classes")
@RequiredArgsConstructor
public class ClassController {

    private final ClassRepository classRepository;
    private final UserRepository userRepository;
    private final StudentClassRepository studentClassRepository;
    private final StudentPerformanceRepository studentPerformanceRepository;
    private final ChapterRepository chapterRepository;
    private final NoteRepository noteRepository;
    private final LectureRepository lectureRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    @PostMapping
    public ResponseEntity<?> createClass(@RequestBody ClassRequest req, Authentication auth) {
        User user = currentUser(auth);
        if (!"teacher".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only teachers can create classes"));
        }

        if (req.name == null || req.name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Class name is required"));
        }

        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        ClassEntity cls = new ClassEntity();
        cls.setName(req.name.trim());
        cls.setTeacherId(user.getId());
        cls.setCode(code);
        cls.setSyllabus(req.syllabus);

        cls = classRepository.save(cls);
        return ResponseEntity.ok(classDto(cls));
    }

    @GetMapping
    public ResponseEntity<?> getClasses(Authentication auth) {
        User user = currentUser(auth);
        List<ClassEntity> classes;

        if ("teacher".equals(user.getRole())) {
            classes = classRepository.findByTeacherIdOrderByCreatedAtDesc(user.getId());
        } else if ("student".equals(user.getRole())) {
            List<String> classIds = studentClassRepository.findByStudentIdOrderByJoinedAtDesc(user.getId())
                    .stream()
                    .map(StudentClass::getClassId)
                    .toList();
            classes = classRepository.findAllById(classIds);
        } else {
            classes = classRepository.findAll();
        }

        return ResponseEntity.ok(classes.stream().map(this::classDto).toList());
    }

    @PostMapping("/join")
    @Transactional
    public ResponseEntity<?> joinClass(@RequestBody JoinClassRequest req, Authentication auth) {
        User user = currentUser(auth);
        if (!"student".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only students can join classes"));
        }
        if (req.classCode == null || req.classCode.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Class code is required"));
        }

        ClassEntity cls = findClassByPastedCode(req.classCode);
        if (cls == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Class not found"));
        }

        if (studentClassRepository.findByStudentIdAndClassId(user.getId(), cls.getId()).isPresent()) {
            return ResponseEntity.ok(Map.of("message", "You already joined this class", "class", classDto(cls), "alreadyJoined", true));
        }

        StudentClass studentClass = new StudentClass();
        studentClass.setStudentId(user.getId());
        studentClass.setClassId(cls.getId());
        studentClassRepository.save(studentClass);
        ensurePerformance(user.getId(), cls.getId());

        return ResponseEntity.ok(Map.of("message", "Joined class", "class", classDto(cls)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getClassDetails(@PathVariable String id) {
        return classRepository.findById(id)
                .<ResponseEntity<?>>map(cls -> ResponseEntity.ok(classDto(cls)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateClass(@PathVariable String id, @RequestBody ClassRequest req, Authentication auth) {
        User user = currentUser(auth);
        return classRepository.findById(id).<ResponseEntity<?>>map(cls -> {
            if (!canManageClass(user, cls)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot update this class"));
            }
            if (req.name != null && !req.name.isBlank()) cls.setName(req.name.trim());
            if (req.syllabus != null) cls.setSyllabus(req.syllabus);
            classRepository.save(cls);
            return ResponseEntity.ok(classDto(cls));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/details")
    public ResponseEntity<?> updateClassDetails(@PathVariable String id, @RequestBody Map<String, String> updates, Authentication auth) {
        User user = currentUser(auth);
        return classRepository.findById(id).<ResponseEntity<?>>map(cls -> {
            if (!canManageClass(user, cls)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot update this class"));
            }
            if (updates.containsKey("syllabus")) cls.setSyllabus(updates.get("syllabus"));
            if (updates.containsKey("name") && !updates.get("name").isBlank()) cls.setName(updates.get("name").trim());
            classRepository.save(cls);
            return ResponseEntity.ok(classDto(cls));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<?> deleteClass(@PathVariable String id, Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(id).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot delete this class"));
        }

        List<String> assignmentIds = assignmentRepository.findByClassIdOrderByCreatedAtDesc(id)
                .stream()
                .map(a -> a.getId())
                .toList();
        if (!assignmentIds.isEmpty()) assignmentSubmissionRepository.deleteByAssignmentIdIn(assignmentIds);
        List<String> quizIds = quizRepository.findByClassIdOrderByCreatedAtDesc(id)
                .stream()
                .map(Quiz::getId)
                .toList();
        if (!quizIds.isEmpty()) {
            quizAttemptRepository.deleteByQuizIdIn(quizIds);
            quizQuestionRepository.deleteByQuizIdIn(quizIds);
        }
        quizRepository.deleteByClassId(id);
        assignmentRepository.deleteByClassId(id);
        chapterRepository.deleteByClassId(id);
        noteRepository.deleteByClassId(id);
        lectureRepository.deleteByClassId(id);
        liveSessionRepository.deleteByClassId(id);
        studentPerformanceRepository.deleteByClassId(id);
        studentClassRepository.deleteByClassId(id);
        classRepository.delete(cls);

        return ResponseEntity.ok(Map.of("message", "Class deleted"));
    }

    @DeleteMapping("/{id}/enrollment")
    @Transactional
    public ResponseEntity<?> unenroll(@PathVariable String id, Authentication auth) {
        User user = currentUser(auth);
        if (!"student".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only students can unenroll"));
        }
        studentClassRepository.deleteByStudentIdAndClassId(user.getId(), id);
        List<String> quizIds = quizRepository.findByClassIdOrderByCreatedAtDesc(id)
                .stream()
                .map(Quiz::getId)
                .toList();
        if (!quizIds.isEmpty()) quizAttemptRepository.deleteByStudentIdAndQuizIdIn(user.getId(), quizIds);
        studentPerformanceRepository.findByStudentIdAndClassId(user.getId(), id)
                .ifPresent(studentPerformanceRepository::delete);
        return ResponseEntity.ok(Map.of("message", "Unenrolled"));
    }

    @GetMapping("/{id}/students")
    public ResponseEntity<?> getClassStudents(@PathVariable String id, Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(id).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot view this class"));
        }

        List<Map<String, Object>> students = studentClassRepository.findByClassId(id).stream()
                .map(sc -> userRepository.findById(sc.getStudentId()).orElse(null))
                .filter(student -> student != null)
                .map(this::userDto)
                .toList();
        return ResponseEntity.ok(students);
    }

    @GetMapping("/{classId}/leaderboard")
    public ResponseEntity<?> getLeaderboard(@PathVariable String classId, Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canViewClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot view this class"));
        }

        List<Map<String, Object>> rows = studentClassRepository.findByClassId(classId).stream()
                .map(sc -> userRepository.findById(sc.getStudentId()).orElse(null))
                .filter(student -> student != null)
                .map(student -> {
                    StudentPerformance performance = ensurePerformance(student.getId(), classId);
                    Map<String, Object> row = userDto(student);
                    row.put("score", performance.getScore() == null ? 0 : performance.getScore());
                    return row;
                })
                .sorted(Comparator.comparingInt(row -> -((Integer) row.get("score"))))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{classId}/student/{studentId}/score")
    public ResponseEntity<?> updateScore(@PathVariable String classId,
                                         @PathVariable String studentId,
                                         @RequestBody ScoreRequest req,
                                         Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot update scores for this class"));
        }
        if (studentClassRepository.findByStudentIdAndClassId(studentId, classId).isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Student is not enrolled in this class"));
        }

        StudentPerformance performance = ensurePerformance(studentId, classId);
        performance.setScore(req.score == null ? 0 : req.score);
        studentPerformanceRepository.save(performance);
        return ResponseEntity.ok(Map.of("message", "Score updated", "score", performance.getScore()));
    }

    private User currentUser(Authentication auth) {
        return (User) auth.getPrincipal();
    }

    private ClassEntity findClassByPastedCode(String input) {
        String normalized = input.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        normalized = normalized.replaceFirst("^(CLASSCODE|CLASS|CODE)", "");
        if (normalized.length() > 6) normalized = normalized.substring(normalized.length() - 6);

        ClassEntity cls = classRepository.findByCode(normalized).orElse(null);
        if (cls != null) return cls;

        String pasted = input.toUpperCase().replaceAll("[^A-Z0-9]", "");
        return classRepository.findAll().stream()
                .filter(candidate -> pasted.contains(candidate.getCode()))
                .findFirst()
                .orElse(null);
    }

    private boolean canManageClass(User user, ClassEntity cls) {
        return "admin".equals(user.getRole()) || ("teacher".equals(user.getRole()) && user.getId().equals(cls.getTeacherId()));
    }

    private boolean canViewClass(User user, ClassEntity cls) {
        return canManageClass(user, cls)
                || studentClassRepository.findByStudentIdAndClassId(user.getId(), cls.getId()).isPresent();
    }

    private StudentPerformance ensurePerformance(String studentId, String classId) {
        return studentPerformanceRepository.findByStudentIdAndClassId(studentId, classId)
                .orElseGet(() -> {
                    StudentPerformance performance = new StudentPerformance();
                    performance.setStudentId(studentId);
                    performance.setClassId(classId);
                    performance.setScore(0);
                    return studentPerformanceRepository.save(performance);
                });
    }

    private Map<String, Object> classDto(ClassEntity cls) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", cls.getId());
        dto.put("name", cls.getName());
        dto.put("teacher_id", cls.getTeacherId());
        dto.put("teacher_name", userRepository.findById(cls.getTeacherId()).map(User::getName).orElse("Teacher"));
        dto.put("code", cls.getCode());
        dto.put("syllabus", cls.getSyllabus());
        dto.put("created_at", cls.getCreatedAt());
        dto.put("student_count", studentClassRepository.countByClassId(cls.getId()));
        dto.put("lecture_count", lectureRepository.countByClassId(cls.getId()));
        dto.put("assignment_count", assignmentRepository.countByClassId(cls.getId()));
        dto.put("quiz_count", quizRepository.countByClassId(cls.getId()));
        return dto;
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

    @Data
    static class ClassRequest {
        String name;
        String syllabus;
    }

    @Data
    static class JoinClassRequest {
        @JsonAlias({"class_code", "classCode"})
        String classCode;
    }

    @Data
    static class ScoreRequest {
        Integer score;
    }
}
