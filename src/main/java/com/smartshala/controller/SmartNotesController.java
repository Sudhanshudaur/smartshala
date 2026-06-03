package com.smartshala.controller;

import com.smartshala.entity.Note;
import com.smartshala.entity.User;
import com.smartshala.repository.NoteRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/notes")
@RequiredArgsConstructor
public class SmartNotesController {

    private final NoteRepository noteRepository;

    @GetMapping
    public ResponseEntity<?> getMyNotes(Authentication auth) {
        User user = (User) auth.getPrincipal();
        return ResponseEntity.ok(noteRepository.findByUserIdOrderByCreatedAtDesc(user.getId()));
    }

    @PostMapping
    public ResponseEntity<?> saveNote(@RequestBody NoteRequest req, Authentication auth) {
        User user = (User) auth.getPrincipal();
        if (req.title == null || req.title.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Title is required"));
        }

        Note note = new Note();
        note.setUserId(user.getId());
        note.setClassId(req.classId);
        note.setTitle(req.title.trim());
        note.setContent(req.content == null ? "" : req.content);
        note.setSummary(req.summary);
        return ResponseEntity.ok(noteRepository.save(note));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNote(@PathVariable String id, @RequestBody NoteRequest req, Authentication auth) {
        User user = (User) auth.getPrincipal();
        return noteRepository.findById(id).<ResponseEntity<?>>map(note -> {
            if (!user.getId().equals(note.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "You cannot edit this note"));
            }
            if (req.title != null && !req.title.isBlank()) note.setTitle(req.title.trim());
            if (req.content != null) note.setContent(req.content);
            if (req.summary != null) note.setSummary(req.summary);
            return ResponseEntity.ok(noteRepository.save(note));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNote(@PathVariable String id, Authentication auth) {
        User user = (User) auth.getPrincipal();
        return noteRepository.findById(id).<ResponseEntity<?>>map(note -> {
            if (!user.getId().equals(note.getUserId())) {
                return ResponseEntity.status(403).body(Map.of("error", "You cannot delete this note"));
            }
            noteRepository.delete(note);
            return ResponseEntity.ok(Map.of("message", "Note deleted"));
        }).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @Data
    static class NoteRequest {
        String title;
        String content;
        String summary;
        String classId;
    }
}
