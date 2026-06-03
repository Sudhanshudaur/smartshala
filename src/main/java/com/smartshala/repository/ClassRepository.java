package com.smartshala.repository;

import com.smartshala.entity.ClassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClassRepository extends JpaRepository<ClassEntity, String> {
    Optional<ClassEntity> findByCode(String code);
    List<ClassEntity> findByTeacherIdOrderByCreatedAtDesc(String teacherId);
    long countByTeacherId(String teacherId);
}
