package com.smartshala.controller;

import com.smartshala.entity.Lecture;
import com.smartshala.entity.StudentClass;
import com.smartshala.entity.User;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.LectureRepository;
import com.smartshala.repository.LiveSessionRepository;
import com.smartshala.repository.StudentClassRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/lectures")
@RequiredArgsConstructor
public class RecordingController {

    private final LectureRepository lectureRepository;
    private final LiveSessionRepository liveSessionRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;

    @Value("${upload.enabled:false}")
    private boolean uploadEnabled;

    // Upload endpoint disabled — returns 503 with clear message
    @PostMapping("/upload")
    public ResponseEntity<?> uploadRecording(@RequestParam(value = "lecture", required = false) MultipartFile file,
                                             @RequestParam(value = "sessionId", required = false) String sessionId,
                                             @RequestParam(value = "classId", required = false) String classId,
                                             @RequestParam(value = "title", required = false) String title,
                                             Authentication auth) {
        if (!uploadEnabled) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Upload feature is coming soon. Stay tuned!"));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("error", "Upload feature is coming soon."));
    }

    @GetMapping
    public ResponseEntity<?> getLectures(Authentication auth) {
        User user = (User) auth.getPrincipal();
        if ("teacher".equals(user.getRole())) {
            return ResponseEntity.ok(lectureRepository.findByTeacherIdOrderByCreatedAtDesc(user.getId()));
        }
        if ("student".equals(user.getRole())) {
            List<String> classIds = studentClassRepository.findByStudentIdOrderByJoinedAtDesc(user.getId())
                    .stream()
                    .map(StudentClass::getClassId)
                    .toList();
            return ResponseEntity.ok(classIds.isEmpty() ? List.of() : lectureRepository.findByClassIdInOrderByCreatedAtDesc(classIds));
        }
        return ResponseEntity.ok(lectureRepository.findByOrderByCreatedAtDesc());
    }
}
