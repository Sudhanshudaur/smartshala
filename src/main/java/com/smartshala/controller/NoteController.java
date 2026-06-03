package com.smartshala.controller;

import com.smartshala.entity.Note;
import com.smartshala.entity.User;
import com.smartshala.repository.NoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/classes/{classId}/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteRepository noteRepository;

    @GetMapping
    public ResponseEntity<List<Note>> getNotes(@PathVariable String classId) {
        return ResponseEntity.ok(noteRepository.findByClassIdOrderByCreatedAtDesc(classId));
    }

    @PostMapping
    public ResponseEntity<?> addNote(@PathVariable String classId, @RequestBody Note noteRequest, Authentication auth) {
        User user = (User) auth.getPrincipal();
        Note note = new Note();
        note.setClassId(classId);
        note.setUserId(user.getId());
        note.setTitle(noteRequest.getTitle());
        note.setContent(noteRequest.getContent());
        return ResponseEntity.ok(noteRepository.save(note));
    }
}
