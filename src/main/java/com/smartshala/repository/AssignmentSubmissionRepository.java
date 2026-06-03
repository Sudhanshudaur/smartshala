package com.smartshala.repository;

import com.smartshala.entity.AssignmentSubmission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, String> {
    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(String assignmentId, String studentId);
    List<AssignmentSubmission> findByAssignmentIdOrderBySubmittedAtDesc(String assignmentId);
    List<AssignmentSubmission> findByStudentIdOrderBySubmittedAtDesc(String studentId);
    List<AssignmentSubmission> findByStudentIdAndAssignmentIdIn(String studentId, List<String> assignmentIds);
    long countByAssignmentIdIn(List<String> assignmentIds);
    void deleteByAssignmentId(String assignmentId);
    void deleteByAssignmentIdIn(List<String> assignmentIds);
    void deleteByStudentId(String studentId);
}
