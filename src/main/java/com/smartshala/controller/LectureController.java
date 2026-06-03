package com.smartshala.controller;

import com.smartshala.entity.Lecture;
import com.smartshala.entity.User;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.LectureRepository;
import com.smartshala.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/classes/{id}/lectures")
@RequiredArgsConstructor
public class LectureController {

    private final LectureRepository lectureRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;

    @Value("${upload.enabled:false}")
    private boolean uploadEnabled;

    // Upload endpoint disabled — returns 503 with clear message
    @PostMapping
    public ResponseEntity<?> uploadLecture(@PathVariable String id,
                                           @RequestParam(value = "lectureFile", required = false) MultipartFile file,
                                           @RequestParam(value = "title", required = false) String title,
                                           @RequestParam(value = "subject", required = false) String subject,
                                           Authentication auth) {
        if (!uploadEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Upload feature is coming soon. Stay tuned!"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Upload feature is coming soon."));
    }

    @GetMapping
    public ResponseEntity<?> getLectures(@PathVariable String id, Authentication auth) {
        User user = (User) auth.getPrincipal();
        boolean canView = classRepository.findById(id)
                .map(cls -> "admin".equals(user.getRole())
                        || user.getId().equals(cls.getTeacherId())
                        || studentClassRepository.findByStudentIdAndClassId(user.getId(), id).isPresent())
                .orElse(false);
        if (!canView) return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("error", "You cannot view lectures for this class"));
        return ResponseEntity.ok(lectureRepository.findByClassIdOrderByCreatedAtDesc(id));
    }
}
