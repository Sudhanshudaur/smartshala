package com.smartshala.controller;

import com.smartshala.entity.Assignment;
import com.smartshala.entity.AssignmentSubmission;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.StudentPerformance;
import com.smartshala.entity.User;
import com.smartshala.repository.AssignmentRepository;
import com.smartshala.repository.AssignmentSubmissionRepository;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.StudentClassRepository;
import com.smartshala.repository.UserRepository;
import com.smartshala.service.PerformanceService;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/classes/{classId}/assignments")
@RequiredArgsConstructor
public class AssignmentController {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;
    private final UserRepository userRepository;
    private final PerformanceService performanceService;

    @Value("${upload.enabled:false}")
    private boolean uploadEnabled;

    @GetMapping
    public ResponseEntity<?> listAssignments(@PathVariable String classId, Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canViewClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot view this class"));
        }
        List<Map<String, Object>> rows = assignmentRepository.findByClassIdOrderByCreatedAtDesc(classId).stream()
                .map(assignment -> assignmentDto(assignment, user))
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping
    public ResponseEntity<?> createAssignment(@PathVariable String classId,
                                              @RequestBody AssignmentRequest req,
                                              Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can create assignments"));
        }
        if (req.title == null || req.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Assignment title is required"));
        }
        Assignment assignment = new Assignment();
        assignment.setClassId(classId);
        assignment.setTeacherId(user.getId());
        assignment.setTitle(req.title.trim());
        assignment.setDescription(req.description);
        assignment.setDueAt(req.dueAt);
        assignment.setMaxScore(req.maxScore == null ? 100 : req.maxScore);
        assignment = assignmentRepository.save(assignment);
        return ResponseEntity.ok(assignmentDto(assignment, user));
    }

    @DeleteMapping("/{assignmentId}")
    @Transactional
    public ResponseEntity<?> deleteAssignment(@PathVariable String classId,
                                              @PathVariable String assignmentId,
                                              Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can delete assignments"));
        }
        Assignment assignment = assignmentRepository.findById(assignmentId).orElse(null);
        if (assignment == null || !classId.equals(assignment.getClassId())) return ResponseEntity.notFound().build();
        assignmentSubmissionRepository.deleteByAssignmentId(assignmentId);
        assignmentRepository.delete(assignment);
        return ResponseEntity.ok(Map.of("message", "Assignment deleted"));
    }

    // File submission disabled — returns 503 with clear message
    @PostMapping("/{assignmentId}/submit")
    public ResponseEntity<?> submitAssignment(@PathVariable String classId,
                                              @PathVariable String assignmentId,
                                              @RequestParam(value = "file", required = false) MultipartFile file,
                                              @RequestParam(value = "notes", required = false) String notes,
                                              Authentication auth) {
        if (!uploadEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Assignment submission with file upload is coming soon. Stay tuned!"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Upload feature is coming soon."));
    }

    @GetMapping("/{assignmentId}/submissions")
    public ResponseEntity<?> listSubmissions(@PathVariable String classId,
                                             @PathVariable String assignmentId,
                                             Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can view submissions"));
        }
        List<Map<String, Object>> rows = assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId)
                .stream()
                .map(this::submissionDto)
                .toList();
        return ResponseEntity.ok(rows);
    }

    @PostMapping("/{assignmentId}/submissions/{submissionId}/grade")
    public ResponseEntity<?> gradeSubmission(@PathVariable String classId,
                                             @PathVariable String assignmentId,
                                             @PathVariable String submissionId,
                                             @RequestBody GradeRequest req,
                                             Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can grade submissions"));
        }
        AssignmentSubmission submission = assignmentSubmissionRepository.findById(submissionId).orElse(null);
        if (submission == null || !assignmentId.equals(submission.getAssignmentId())) return ResponseEntity.notFound().build();
        submission.setScore(req.score == null ? 0 : req.score);
        submission.setFeedback(req.feedback);
        submission.setGradedAt(new Date());
        assignmentSubmissionRepository.save(submission);
        performanceService.recalculate(submission.getStudentId(), classId);
        return ResponseEntity.ok(submissionDto(submission));
    }

    @GetMapping("/grades")
    public ResponseEntity<?> getGrades(@PathVariable String classId, Authentication auth) {
        User user = currentUser(auth);
        if (!"student".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only students can view their grades here"));
        }
        if (studentClassRepository.findByStudentIdAndClassId(user.getId(), classId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not enrolled in this class"));
        }
        List<Assignment> assignments = assignmentRepository.findByClassIdOrderByCreatedAtDesc(classId);
        List<Map<String, Object>> grades = assignments.stream()
                .map(assignment -> {
                    Map<String, Object> row = assignmentDto(assignment, user);
                    assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), user.getId())
                            .ifPresent(submission -> row.put("submission", submissionDto(submission)));
                    return row;
                })
                .toList();
        StudentPerformance performance = performanceService.recalculate(user.getId(), classId);
        return ResponseEntity.ok(Map.of(
                "overallScore", performance == null || performance.getScore() == null ? 0 : performance.getScore(),
                "grades", grades
        ));
    }

    private Map<String, Object> assignmentDto(Assignment assignment, User currentUser) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", assignment.getId());
        dto.put("class_id", assignment.getClassId());
        dto.put("teacher_id", assignment.getTeacherId());
        dto.put("title", assignment.getTitle());
        dto.put("description", assignment.getDescription());
        dto.put("due_at", assignment.getDueAt());
        dto.put("max_score", assignment.getMaxScore());
        dto.put("created_at", assignment.getCreatedAt());
        dto.put("submission_count", assignmentSubmissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignment.getId()).size());
        if ("student".equals(currentUser.getRole())) {
            assignmentSubmissionRepository.findByAssignmentIdAndStudentId(assignment.getId(), currentUser.getId())
                    .ifPresent(submission -> dto.put("submission", submissionDto(submission)));
        }
        return dto;
    }

    private Map<String, Object> submissionDto(AssignmentSubmission submission) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", submission.getId());
        dto.put("assignment_id", submission.getAssignmentId());
        dto.put("student_id", submission.getStudentId());
        dto.put("student", userRepository.findById(submission.getStudentId()).map(this::studentDto).orElse(null));
        dto.put("file_url", submission.getFileUrl());
        dto.put("file_name", submission.getFileName());
        dto.put("notes", submission.getNotes());
        dto.put("score", submission.getScore());
        dto.put("feedback", submission.getFeedback());
        dto.put("submitted_at", submission.getSubmittedAt());
        dto.put("graded_at", submission.getGradedAt());
        return dto;
    }

    private Map<String, Object> studentDto(User student) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", student.getId());
        dto.put("name", student.getName());
        dto.put("email", student.getEmail());
        return dto;
    }

    private boolean canManageClass(User user, ClassEntity cls) {
        return "admin".equals(user.getRole()) || ("teacher".equals(user.getRole()) && user.getId().equals(cls.getTeacherId()));
    }

    private boolean canViewClass(User user, ClassEntity cls) {
        return canManageClass(user, cls)
                || studentClassRepository.findByStudentIdAndClassId(user.getId(), cls.getId()).isPresent();
    }

    private User currentUser(Authentication auth) {
        return (User) auth.getPrincipal();
    }

    @Data
    static class AssignmentRequest {
        String title;
        String description;
        Date dueAt;
        Integer maxScore;
    }

    @Data
    static class GradeRequest {
        Integer score;
        String feedback;
    }
}
