package com.smartshala.repository;

import com.smartshala.entity.Lecture;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface LectureRepository extends JpaRepository<Lecture, String> {
    List<Lecture> findByClassIdOrderByCreatedAtDesc(String classId);
    List<Lecture> findByClassIdInOrderByCreatedAtDesc(List<String> classIds);
    List<Lecture> findByTeacherIdOrderByCreatedAtDesc(String teacherId);
    List<Lecture> findByOrderByCreatedAtDesc();
    long countByClassId(String classId);
    void deleteByClassId(String classId);
}
