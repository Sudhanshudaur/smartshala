package com.smartshala.service;

import com.smartshala.entity.Assignment;
import com.smartshala.entity.AssignmentSubmission;
import com.smartshala.entity.Quiz;
import com.smartshala.entity.QuizAttempt;
import com.smartshala.entity.StudentPerformance;
import com.smartshala.repository.AssignmentRepository;
import com.smartshala.repository.AssignmentSubmissionRepository;
import com.smartshala.repository.QuizAttemptRepository;
import com.smartshala.repository.QuizRepository;
import com.smartshala.repository.StudentPerformanceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformanceService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository assignmentSubmissionRepository;
    private final QuizRepository quizRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final StudentPerformanceRepository studentPerformanceRepository;

    @Transactional
    public StudentPerformance recalculate(String studentId, String classId) {
        int total = assignmentScore(studentId, classId) + quizScore(studentId, classId);

        StudentPerformance performance = studentPerformanceRepository.findByStudentIdAndClassId(studentId, classId)
                .orElseGet(() -> {
                    StudentPerformance p = new StudentPerformance();
                    p.setStudentId(studentId);
                    p.setClassId(classId);
                    return p;
                });
        performance.setScore(total);
        return studentPerformanceRepository.save(performance);
    }

    private int assignmentScore(String studentId, String classId) {
        List<String> assignmentIds = assignmentRepository.findByClassIdOrderByCreatedAtDesc(classId)
                .stream()
                .map(Assignment::getId)
                .toList();
        if (assignmentIds.isEmpty()) return 0;

        return assignmentSubmissionRepository.findByStudentIdAndAssignmentIdIn(studentId, assignmentIds)
                .stream()
                .filter(submission -> submission.getScore() != null)
                .mapToInt(AssignmentSubmission::getScore)
                .sum();
    }

    private int quizScore(String studentId, String classId) {
        List<String> quizIds = quizRepository.findByClassIdOrderByCreatedAtDesc(classId)
                .stream()
                .map(Quiz::getId)
                .toList();
        if (quizIds.isEmpty()) return 0;

        return quizAttemptRepository.findByStudentIdAndQuizIdIn(studentId, quizIds)
                .stream()
                .filter(attempt -> attempt.getScore() != null)
                .mapToInt(QuizAttempt::getScore)
                .sum();
    }
}
