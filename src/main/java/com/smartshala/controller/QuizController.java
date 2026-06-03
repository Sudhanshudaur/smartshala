package com.smartshala.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartshala.entity.Chapter;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.Note;
import com.smartshala.entity.Quiz;
import com.smartshala.entity.QuizAttempt;
import com.smartshala.entity.QuizQuestion;
import com.smartshala.entity.StudentPerformance;
import com.smartshala.entity.User;
import com.smartshala.repository.ChapterRepository;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.NoteRepository;
import com.smartshala.repository.QuizAttemptRepository;
import com.smartshala.repository.QuizQuestionRepository;
import com.smartshala.repository.QuizRepository;
import com.smartshala.repository.StudentClassRepository;
import com.smartshala.repository.UserRepository;
import com.smartshala.service.PerformanceService;
import jakarta.transaction.Transactional;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/classes/{classId}/quizzes")
@RequiredArgsConstructor
public class QuizController {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;
    private final ChapterRepository chapterRepository;
    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final PerformanceService performanceService;
    private final ObjectMapper objectMapper;

    @GetMapping
    public ResponseEntity<?> listQuizzes(@PathVariable String classId, Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canViewClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You cannot view quizzes for this class"));
        }

        boolean includeAnswers = canManageClass(user, cls);
        List<Map<String, Object>> quizzes = quizRepository.findByClassIdOrderByCreatedAtDesc(classId)
                .stream()
                .map(quiz -> quizDto(quiz, user, includeAnswers))
                .toList();
        return ResponseEntity.ok(quizzes);
    }

    @PostMapping("/generate")
    @Transactional
    public ResponseEntity<?> generateQuiz(@PathVariable String classId,
                                          @RequestBody GenerateQuizRequest req,
                                          Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can generate quizzes"));
        }

        int questionCount = clamp(req.questionCount == null ? 5 : req.questionCount, 3, 10);
        int maxScore = clamp(req.maxScore == null ? questionCount * 10 : req.maxScore, questionCount, 200);
        String sourceText = buildSourceText(cls, req);
        String topic = normalize(req.topic).isBlank() ? cls.getName() : normalize(req.topic);
        String title = normalize(req.title).isBlank() ? "AI Quiz: " + topic : normalize(req.title);

        Quiz quiz = new Quiz();
        quiz.setClassId(classId);
        quiz.setTeacherId(user.getId());
        quiz.setTitle(trimTo(title, 180));
        quiz.setSourceTitle(trimTo(topic, 180));
        quiz.setQuestionCount(questionCount);
        quiz.setMaxScore(maxScore);
        quiz = quizRepository.save(quiz);

        List<QuizQuestion> questions = generateQuestions(quiz, sourceText, topic, questionCount);
        quizQuestionRepository.saveAll(questions);

        return ResponseEntity.ok(quizDto(quiz, user, true));
    }

    @GetMapping("/{quizId}/attempts")
    public ResponseEntity<?> listAttempts(@PathVariable String classId,
                                          @PathVariable String quizId,
                                          Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can view quiz attempts"));
        }
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null || !classId.equals(quiz.getClassId())) return ResponseEntity.notFound().build();

        return ResponseEntity.ok(quizAttemptRepository.findByQuizIdOrderBySubmittedAtDesc(quizId)
                .stream()
                .map(this::attemptDto)
                .toList());
    }

    @PostMapping("/{quizId}/submit")
    @Transactional
    public ResponseEntity<?> submitQuiz(@PathVariable String classId,
                                        @PathVariable String quizId,
                                        @RequestBody SubmitQuizRequest req,
                                        Authentication auth) {
        User user = currentUser(auth);
        if (!"student".equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only students can submit quizzes"));
        }
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (studentClassRepository.findByStudentIdAndClassId(user.getId(), classId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "You are not enrolled in this class"));
        }
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null || !classId.equals(quiz.getClassId())) return ResponseEntity.notFound().build();

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quizId);
        if (questions.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "This quiz has no questions"));
        }

        Map<String, String> answers = req.answers == null ? Map.of() : req.answers;
        int correctCount = 0;
        for (QuizQuestion question : questions) {
            String selected = normalizeAnswer(answers.get(question.getId()), question);
            if (question.getCorrectOption().equals(selected)) correctCount++;
        }

        int maxScore = quiz.getMaxScore() == null ? questions.size() * 10 : quiz.getMaxScore();
        int score = Math.round((correctCount * 1.0f / questions.size()) * maxScore);

        QuizAttempt attempt = quizAttemptRepository.findByQuizIdAndStudentId(quizId, user.getId())
                .orElseGet(QuizAttempt::new);
        attempt.setQuizId(quizId);
        attempt.setStudentId(user.getId());
        attempt.setScore(score);
        attempt.setMaxScore(maxScore);
        attempt.setCorrectCount(correctCount);
        attempt.setTotalQuestions(questions.size());
        attempt.setFeedback(correctCount + "/" + questions.size() + " correct");
        try {
            attempt.setAnswersJson(objectMapper.writeValueAsString(answers));
        } catch (JsonProcessingException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "Could not save quiz answers"));
        }
        attempt = quizAttemptRepository.save(attempt);

        StudentPerformance performance = performanceService.recalculate(user.getId(), classId);
        return ResponseEntity.ok(Map.of(
                "message", "Quiz submitted",
                "attempt", attemptDto(attempt),
                "leaderboardScore", performance.getScore() == null ? 0 : performance.getScore(),
                "questions", questions.stream().map(question -> questionDto(question, true)).toList()
        ));
    }

    @DeleteMapping("/{quizId}")
    @Transactional
    public ResponseEntity<?> deleteQuiz(@PathVariable String classId,
                                        @PathVariable String quizId,
                                        Authentication auth) {
        User user = currentUser(auth);
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return ResponseEntity.notFound().build();
        if (!canManageClass(user, cls)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "Only the class teacher can delete quizzes"));
        }
        Quiz quiz = quizRepository.findById(quizId).orElse(null);
        if (quiz == null || !classId.equals(quiz.getClassId())) return ResponseEntity.notFound().build();

        List<String> affectedStudentIds = quizAttemptRepository.findByQuizIdOrderBySubmittedAtDesc(quizId)
                .stream()
                .map(QuizAttempt::getStudentId)
                .distinct()
                .toList();

        quizAttemptRepository.deleteByQuizId(quizId);
        quizQuestionRepository.deleteByQuizId(quizId);
        quizRepository.delete(quiz);
        affectedStudentIds.forEach(studentId -> performanceService.recalculate(studentId, classId));

        return ResponseEntity.ok(Map.of("message", "Quiz deleted"));
    }

    private List<QuizQuestion> generateQuestions(Quiz quiz, String sourceText, String topic, int questionCount) {
        List<String> sentences = splitSentences(sourceText);
        List<String> keywords = keywords(sourceText, Math.max(questionCount * 2, 8));
        if (sentences.isEmpty()) sentences = List.of("Review the main ideas, definitions, examples, and applications from " + topic + ".");
        if (keywords.isEmpty()) keywords = List.of(topic.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim());

        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            String keyword = normalize(keywords.get(i % keywords.size()));
            if (keyword.isBlank()) keyword = "the topic";
            String readableKeyword = titleCase(keyword);
            String sentence = trimTo(sentences.get(i % sentences.size()), 180);
            List<String> options = new ArrayList<>(List.of(
                    sentence,
                    readableKeyword + " is only a label and does not affect the lesson.",
                    readableKeyword + " should be skipped until after the class is complete.",
                    "The material says " + readableKeyword.toLowerCase(Locale.ROOT) + " is unrelated to the current topic."
            ));
            int correctIndex = i % options.size();
            String correctOption = optionLabel(correctIndex);
            String correctText = options.get(0);
            options.set(0, options.get(correctIndex));
            options.set(correctIndex, correctText);

            QuizQuestion question = new QuizQuestion();
            question.setQuizId(quiz.getId());
            question.setQuestionOrder(i + 1);
            question.setPrompt(promptFor(i, readableKeyword, topic));
            question.setOptionA(options.get(0));
            question.setOptionB(options.get(1));
            question.setOptionC(options.get(2));
            question.setOptionD(options.get(3));
            question.setCorrectOption(correctOption);
            question.setExplanation("Generated from class material mentioning " + readableKeyword + ".");
            questions.add(question);
        }
        return questions;
    }

    private String buildSourceText(ClassEntity cls, GenerateQuizRequest req) {
        StringBuilder text = new StringBuilder();
        if (!normalize(req.sourceText).isBlank()) text.append(req.sourceText).append(" ");
        if (cls.getSyllabus() != null) text.append(cls.getSyllabus()).append(" ");
        chapterRepository.findByClassIdOrderByCreatedAtAsc(cls.getId()).forEach(chapter -> {
            text.append(chapter.getTitle()).append(". ");
            if (chapter.getContent() != null) text.append(chapter.getContent()).append(" ");
        });
        noteRepository.findByClassIdOrderByCreatedAtDesc(cls.getId()).forEach(note -> {
            text.append(note.getTitle()).append(". ");
            if (note.getSummary() != null) text.append(note.getSummary()).append(" ");
            if (note.getContent() != null) text.append(note.getContent()).append(" ");
        });

        String normalized = normalize(text.toString());
        if (!normalized.isBlank()) return normalized;
        String topic = normalize(req.topic).isBlank() ? cls.getName() : normalize(req.topic);
        return topic + " includes important definitions, examples, steps, causes, effects, and review points for students.";
    }

    private Map<String, Object> quizDto(Quiz quiz, User currentUser, boolean includeAnswers) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", quiz.getId());
        dto.put("class_id", quiz.getClassId());
        dto.put("teacher_id", quiz.getTeacherId());
        dto.put("title", quiz.getTitle());
        dto.put("source_title", quiz.getSourceTitle());
        dto.put("max_score", quiz.getMaxScore());
        dto.put("question_count", quiz.getQuestionCount());
        dto.put("created_at", quiz.getCreatedAt());
        dto.put("attempt_count", quizAttemptRepository.findByQuizIdOrderBySubmittedAtDesc(quiz.getId()).size());
        dto.put("questions", quizQuestionRepository.findByQuizIdOrderByQuestionOrderAsc(quiz.getId())
                .stream()
                .map(question -> questionDto(question, includeAnswers))
                .toList());
        if ("student".equals(currentUser.getRole())) {
            quizAttemptRepository.findByQuizIdAndStudentId(quiz.getId(), currentUser.getId())
                    .ifPresent(attempt -> dto.put("attempt", attemptDto(attempt)));
        }
        return dto;
    }

    private Map<String, Object> questionDto(QuizQuestion question, boolean includeAnswer) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", question.getId());
        dto.put("prompt", question.getPrompt());
        dto.put("options", List.of(
                Map.of("key", "A", "text", question.getOptionA()),
                Map.of("key", "B", "text", question.getOptionB()),
                Map.of("key", "C", "text", question.getOptionC()),
                Map.of("key", "D", "text", question.getOptionD())
        ));
        if (includeAnswer) {
            dto.put("correct_option", question.getCorrectOption());
            dto.put("explanation", question.getExplanation());
        }
        return dto;
    }

    private Map<String, Object> attemptDto(QuizAttempt attempt) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", attempt.getId());
        dto.put("quiz_id", attempt.getQuizId());
        dto.put("student_id", attempt.getStudentId());
        dto.put("student", userRepository.findById(attempt.getStudentId()).map(this::studentDto).orElse(null));
        dto.put("score", attempt.getScore());
        dto.put("max_score", attempt.getMaxScore());
        dto.put("correct_count", attempt.getCorrectCount());
        dto.put("total_questions", attempt.getTotalQuestions());
        dto.put("feedback", attempt.getFeedback());
        dto.put("submitted_at", attempt.getSubmittedAt());
        return dto;
    }

    private Map<String, Object> studentDto(User student) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", student.getId());
        dto.put("name", student.getName());
        dto.put("email", student.getEmail());
        return dto;
    }

    private boolean canManageClass(User user, ClassEntity cls) {
        return "admin".equals(user.getRole()) || ("teacher".equals(user.getRole()) && user.getId().equals(cls.getTeacherId()));
    }

    private boolean canViewClass(User user, ClassEntity cls) {
        return canManageClass(user, cls)
                || studentClassRepository.findByStudentIdAndClassId(user.getId(), cls.getId()).isPresent();
    }

    private User currentUser(Authentication auth) {
        return (User) auth.getPrincipal();
    }

    private String normalizeAnswer(String value, QuizQuestion question) {
        if (value == null) return "";
        String answer = value.trim();
        if (answer.matches("^[A-Da-d]$")) return answer.toUpperCase(Locale.ROOT);
        if (answer.matches("^[0-3]$")) return optionLabel(Integer.parseInt(answer));
        String normalized = normalize(answer);
        if (normalized.equals(normalize(question.getOptionA()))) return "A";
        if (normalized.equals(normalize(question.getOptionB()))) return "B";
        if (normalized.equals(normalize(question.getOptionC()))) return "C";
        if (normalized.equals(normalize(question.getOptionD()))) return "D";
        return answer.toUpperCase(Locale.ROOT);
    }

    private String promptFor(int index, String keyword, String topic) {
        return switch (index % 4) {
            case 0 -> "What does the class material emphasize about " + keyword + "?";
            case 1 -> "Which statement is most accurate for " + keyword + " in " + topic + "?";
            case 2 -> "What should students remember about " + keyword + "?";
            default -> "Which option best matches the lesson point for " + keyword + "?";
        };
    }

    private List<String> splitSentences(String text) {
        String normalized = normalize(text);
        List<String> sentences = Arrays.stream(normalized.split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(sentence -> sentence.length() > 25)
                .toList();
        if (!sentences.isEmpty()) return sentences;

        List<String> chunks = new ArrayList<>();
        for (int i = 0; i < normalized.length(); i += 180) {
            chunks.add(normalized.substring(i, Math.min(i + 180, normalized.length())));
        }
        return chunks.stream().filter(chunk -> !chunk.isBlank()).toList();
    }

    private List<String> keywords(String text, int limit) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 3 && !STOP_WORDS.contains(word))
                .collect(Collectors.groupingBy(word -> word, LinkedHashMap::new, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry::getKey))
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private String trimTo(String text, int maxLength) {
        String normalized = normalize(text);
        if (normalized.length() <= maxLength) return normalized;
        return normalized.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private String titleCase(String text) {
        return Arrays.stream(normalize(text).split("\\s+"))
                .filter(word -> !word.isBlank())
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String optionLabel(int index) {
        return String.valueOf((char) ('A' + index));
    }

    private static final List<String> STOP_WORDS = List.of(
            "this", "that", "with", "from", "have", "will", "your", "about", "there", "their",
            "which", "when", "what", "were", "been", "into", "also", "because", "should", "class",
            "student", "teacher", "lecture", "notes", "material"
    );

    @Data
    static class GenerateQuizRequest {
        String title;
        String topic;
        @JsonAlias({"source_text", "sourceText"})
        String sourceText;
        @JsonAlias({"question_count", "questionCount"})
        Integer questionCount;
        @JsonAlias({"max_score", "maxScore"})
        Integer maxScore;
    }

    @Data
    static class SubmitQuizRequest {
        Map<String, String> answers;
    }
}
