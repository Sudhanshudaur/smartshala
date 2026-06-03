package com.smartshala.repository;

import com.smartshala.entity.Chapter;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ChapterRepository extends JpaRepository<Chapter, String> {
    List<Chapter> findByClassIdOrderByCreatedAtAsc(String classId);
    void deleteByClassId(String classId);
}
