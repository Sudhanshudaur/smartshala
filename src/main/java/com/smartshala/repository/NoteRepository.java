package com.smartshala.repository;

import com.smartshala.entity.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface NoteRepository extends JpaRepository<Note, String> {
    List<Note> findByClassIdOrderByCreatedAtDesc(String classId);
    List<Note> findByUserIdOrderByCreatedAtDesc(String userId);
    void deleteByClassId(String classId);
    void deleteByUserId(String userId);
}
