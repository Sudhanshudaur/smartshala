package com.smartshala.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.User;
import com.smartshala.repository.ClassRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final ClassRepository classRepository;

    @PostMapping("/summarize")
    public ResponseEntity<?> summarize(@RequestBody TextRequest req) {
        String text = normalize(req.text);
        if (text.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Text is required"));
        }

        List<String> sentences = splitSentences(text);
        List<String> keySentences = pickKeySentences(sentences, 4);
        List<String> keywords = keywords(text, 8);

        String summary = keySentences.isEmpty()
                ? text.substring(0, Math.min(text.length(), 300))
                : String.join(" ", keySentences);

        List<String> actionItems = keywords.stream()
                .limit(4)
                .map(word -> "Review " + word)
                .toList();

        return ResponseEntity.ok(Map.of(
                "summary", summary,
                "keyPoints", keySentences,
                "keywords", keywords,
                "actionItems", actionItems
        ));
    }

    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody ChatRequest req, Authentication auth) {
        User user = (User) auth.getPrincipal();
        String message = normalize(req.message);
        if (message.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Message is required"));
        }

        String lower = message.toLowerCase(Locale.ROOT);
        String className = req.classId == null ? "this class" : classRepository.findById(req.classId).map(ClassEntity::getName).orElse("this class");
        String reply;

        if (lower.contains("summar") || lower.contains("notes")) {
            reply = "Here is a quick study summary for " + className + ": " + String.join(" ", pickKeySentences(splitSentences(message), 3));
            if (reply.endsWith(": ")) reply += "Add your lecture notes or a topic, and I will turn it into a concise revision note.";
        } else if (lower.contains("assignment") || lower.contains("homework")) {
            reply = "Open the class page and check Assignments & Grades. Teachers can create tasks there, and students can upload submissions from the same section.";
        } else if (lower.contains("lecture") || lower.contains("recording")) {
            reply = "Recorded lectures are available in Lectures. Teachers can upload recordings; students can watch or download lectures for their enrolled classes.";
        } else if (lower.contains("live") || lower.contains("camera") || lower.contains("audio")) {
            reply = "For live class, join the room, allow camera and microphone access, and use the chat panel for questions. Teachers can record and end sessions.";
        } else if (lower.contains("grade") || lower.contains("score")) {
            reply = "Students can view grades inside each class under Assignments & Grades. Teachers can grade assignment submissions and update leaderboard scores.";
        } else {
            reply = "I can help with " + className + ". Ask me to summarize notes, explain an assignment, find lecture access, or clarify live-class steps.";
        }

        return ResponseEntity.ok(Map.of(
                "reply", reply,
                "from", "Smartshala AI",
                "role", user.getRole()
        ));
    }

    @PostMapping("/path-finder")
    public ResponseEntity<?> pathFinder(@RequestBody PathFinderRequest req, Authentication auth) {
        User user = (User) auth.getPrincipal();
        String goal = normalize(req.goal);
        if (goal.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Learning goal is required"));
        }

        ClassEntity cls = resolveClass(req.classId);
        String className = cls == null ? "your course" : cls.getName();
        String source = normalize(goal + " " + req.focusAreas + " " + (cls == null ? "" : cls.getSyllabus()));
        List<String> topics = keywords(source, 8);
        if (topics.isEmpty()) topics = keywords(goal, 8);
        if (topics.isEmpty()) topics = List.of(goal);

        String level = normalize(req.currentLevel).isBlank() ? "beginner" : normalize(req.currentLevel).toLowerCase(Locale.ROOT);
        int weeklyHours = clamp(req.weeklyHours == null ? 6 : req.weeklyHours, 2, 40);
        int phaseCount = Math.min(6, Math.max(4, topics.size()));
        List<Map<String, Object>> path = new ArrayList<>();
        for (int i = 0; i < phaseCount; i++) {
            String topic = titleCase(topics.get(i % topics.size()));
            path.add(Map.of(
                    "week", i + 1,
                    "title", phaseTitle(i, topic),
                    "focus", topic,
                    "tasks", List.of(
                            "Read and summarize the core idea of " + topic,
                            "Practice 3-5 questions or examples on " + topic,
                            "Explain " + topic + " in your own words",
                            i % 2 == 0 ? "Create a short note for revision" : "Attempt a timed mini quiz"
                    ),
                    "targetHours", Math.max(2, weeklyHours / 2),
                    "checkpoint", checkpointFor(i, topic)
            ));
        }

        return ResponseEntity.ok(Map.of(
                "studentRole", user.getRole(),
                "className", className,
                "goal", goal,
                "level", level,
                "summary", "A focused path for " + className + " that moves from foundations to practice and revision.",
                "path", path,
                "milestones", List.of(
                        "Baseline check: identify weak topics",
                        "Midpoint check: complete one quiz or assignment",
                        "Final check: revise mistakes and retake practice questions"
                ),
                "tips", List.of(
                        "Keep each study block small and measurable",
                        "Review wrong answers within 24 hours",
                        "Use class notes before searching outside material"
                )
        ));
    }

    @PostMapping("/timetable")
    public ResponseEntity<?> timetable(@RequestBody TimetableRequest req, Authentication auth) {
        User user = (User) auth.getPrincipal();
        String goal = normalize(req.goal);
        if (goal.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Study goal is required"));
        }

        ClassEntity cls = resolveClass(req.classId);
        String source = normalize(goal + " " + req.focusAreas + " " + (cls == null ? "" : cls.getSyllabus()));
        List<String> topics = keywords(source, 10);
        if (topics.isEmpty()) topics = List.of(goal);

        int days = clamp(req.days == null ? 7 : req.days, 1, 30);
        int hoursPerDay = clamp(req.hoursPerDay == null ? 2 : req.hoursPerDay, 1, 8);
        String preferredTime = normalize(req.preferredTime).isBlank() ? "Evening" : normalize(req.preferredTime);
        LocalDate start = parseDate(req.startDate);

        List<Map<String, Object>> schedule = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            String topic = titleCase(topics.get(i % topics.size()));
            boolean revisionDay = (i + 1) % 4 == 0 || i == days - 1;
            schedule.add(Map.of(
                    "day", i + 1,
                    "date", start.plusDays(i).toString(),
                    "time", preferredTime,
                    "hours", hoursPerDay,
                    "topic", revisionDay ? "Revision and practice" : topic,
                    "plan", revisionDay
                            ? List.of("Review notes", "Fix mistakes", "Take a short quiz", "Update doubt list")
                            : List.of("Concept study: " + topic, "Worked examples", "Practice problems", "Quick recap"),
                    "output", revisionDay ? "Mistake list and improved score" : "One-page note plus solved examples"
            ));
        }

        return ResponseEntity.ok(Map.of(
                "role", user.getRole(),
                "className", cls == null ? "General Study" : cls.getName(),
                "goal", goal,
                "totalHours", days * hoursPerDay,
                "schedule", schedule,
                "reminders", List.of(
                        "Keep phone away during each study block",
                        "Mark completed sessions the same day",
                        "Move unfinished work to the next revision day"
                )
        ));
    }

    private String normalize(String text) {
        return text == null ? "" : text.replaceAll("\\s+", " ").trim();
    }

    private List<String> splitSentences(String text) {
        return Arrays.stream(text.split("(?<=[.!?])\\s+"))
                .map(String::trim)
                .filter(sentence -> sentence.length() > 20)
                .toList();
    }

    private List<String> pickKeySentences(List<String> sentences, int limit) {
        Map<String, Long> frequency = sentences.stream()
                .flatMap(sentence -> Arrays.stream(sentence.toLowerCase(Locale.ROOT).split("[^a-z0-9]+")))
                .filter(word -> word.length() > 3 && !STOP_WORDS.contains(word))
                .collect(Collectors.groupingBy(word -> word, LinkedHashMap::new, Collectors.counting()));

        return sentences.stream()
                .sorted(Comparator.comparingLong((String sentence) -> score(sentence, frequency)).reversed())
                .limit(limit)
                .toList();
    }

    private long score(String sentence, Map<String, Long> frequency) {
        return Arrays.stream(sentence.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .mapToLong(word -> frequency.getOrDefault(word, 0L))
                .sum();
    }

    private List<String> keywords(String text, int limit) {
        return Arrays.stream(text.toLowerCase(Locale.ROOT).split("[^a-z0-9]+"))
                .filter(word -> word.length() > 3 && !STOP_WORDS.contains(word))
                .collect(Collectors.groupingBy(word -> word, Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();
    }

    private ClassEntity resolveClass(String classId) {
        return classId == null || classId.isBlank() ? null : classRepository.findById(classId).orElse(null);
    }

    private String phaseTitle(int index, String topic) {
        return switch (index) {
            case 0 -> "Foundation: " + topic;
            case 1 -> "Core Concepts: " + topic;
            case 2 -> "Practice: " + topic;
            case 3 -> "Application: " + topic;
            case 4 -> "Revision: " + topic;
            default -> "Final Prep: " + topic;
        };
    }

    private String checkpointFor(int index, String topic) {
        return switch (index % 3) {
            case 0 -> "Explain " + topic + " without notes";
            case 1 -> "Score at least 70% on practice questions";
            default -> "Complete one mixed revision set";
        };
    }

    private LocalDate parseDate(String value) {
        try {
            return value == null || value.isBlank() ? LocalDate.now() : LocalDate.parse(value);
        } catch (Exception e) {
            return LocalDate.now();
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String titleCase(String text) {
        String normalized = normalize(text);
        if (normalized.isBlank()) return "Topic";
        return Arrays.stream(normalized.split("\\s+"))
                .map(word -> word.substring(0, 1).toUpperCase(Locale.ROOT) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static final List<String> STOP_WORDS = List.of(
            "this", "that", "with", "from", "have", "will", "your", "about", "there", "their",
            "which", "when", "what", "were", "been", "into", "also", "because", "should", "class"
    );

    @Data
    static class TextRequest {
        String text;
    }

    @Data
    static class ChatRequest {
        String message;
        String classId;
    }

    @Data
    static class PathFinderRequest {
        @JsonAlias({"class_id", "classId"})
        String classId;
        String goal;
        @JsonAlias({"current_level", "currentLevel"})
        String currentLevel;
        @JsonAlias({"weekly_hours", "weeklyHours"})
        Integer weeklyHours;
        @JsonAlias({"focus_areas", "focusAreas"})
        String focusAreas;
    }

    @Data
    static class TimetableRequest {
        @JsonAlias({"class_id", "classId"})
        String classId;
        String goal;
        Integer days;
        @JsonAlias({"hours_per_day", "hoursPerDay"})
        Integer hoursPerDay;
        @JsonAlias({"preferred_time", "preferredTime"})
        String preferredTime;
        @JsonAlias({"start_date", "startDate"})
        String startDate;
        @JsonAlias({"focus_areas", "focusAreas"})
        String focusAreas;
    }
}
