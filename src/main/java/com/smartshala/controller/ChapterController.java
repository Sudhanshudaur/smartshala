package com.smartshala.controller;

import com.smartshala.entity.Chapter;
import com.smartshala.repository.ChapterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes/{classId}/chapters")
@RequiredArgsConstructor
public class ChapterController {

    private final ChapterRepository chapterRepository;

    @GetMapping
    public ResponseEntity<List<Chapter>> getChapters(@PathVariable String classId) {
        return ResponseEntity.ok(chapterRepository.findByClassIdOrderByCreatedAtAsc(classId));
    }

    @PostMapping
    public ResponseEntity<?> addChapter(@PathVariable String classId, @RequestBody Chapter chapterRequest) {
        Chapter chapter = new Chapter();
        chapter.setClassId(classId);
        chapter.setTitle(chapterRequest.getTitle());
        chapter.setContent(chapterRequest.getContent());
        return ResponseEntity.ok(chapterRepository.save(chapter));
    }
}
