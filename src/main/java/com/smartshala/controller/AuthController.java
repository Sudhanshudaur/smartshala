package com.smartshala.controller;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.StudentClass;
import com.smartshala.entity.User;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.StudentClassRepository;
import com.smartshala.repository.UserRepository;
import com.smartshala.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest req) {
        if (req.email == null || req.password == null || req.role == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing fields"));
        }
        req.email = req.email.trim().toLowerCase();
        req.role = req.role.trim().toLowerCase();

        if (!"student".equals(req.role) && !"teacher".equals(req.role)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Public registration is only available for students and teachers"));
        }

        if (userRepository.findByEmail(req.email).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Email already in use"));
        }

        String initialClassId = null;
        if ("student".equals(req.role) && req.classCode != null && !req.classCode.isEmpty()) {
            Optional<ClassEntity> optClass = classRepository.findByCode(req.classCode);
            if (optClass.isPresent()) {
                initialClassId = optClass.get().getId();
            } else {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid class code"));
            }
        }

        User user = new User();
        user.setName(req.name != null ? req.name : "");
        user.setEmail(req.email);
        user.setPasswordHash(passwordEncoder.encode(req.password));
        user.setRole(req.role);

        user = userRepository.save(user);

        if (initialClassId != null) {
            StudentClass sc = new StudentClass();
            sc.setStudentId(user.getId());
            sc.setClassId(initialClassId);
            studentClassRepository.save(sc);
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());

        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("user", userMap);

        return ResponseEntity.ok(resp);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest req) {
        if (req.email == null || req.password == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing email or password"));
        }
        req.email = req.email.trim().toLowerCase();
        if (req.role != null) req.role = req.role.trim().toLowerCase();

        Optional<User> optUser = userRepository.findByEmail(req.email);
        if (optUser.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }

        User user = optUser.get();
        if (user.getPasswordHash() == null || !passwordEncoder.matches(req.password, user.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        if (req.role != null && !req.role.isBlank() && !req.role.equals(user.getRole())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid role for this account"));
        }

        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());

        String token = jwtUtil.generateToken(user.getId(), user.getRole());
        Map<String, Object> resp = new HashMap<>();
        resp.put("token", token);
        resp.put("user", userMap);

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getMe(Authentication auth) {
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        User user = (User) auth.getPrincipal();
        Map<String, Object> userMap = new HashMap<>();
        userMap.put("id", user.getId());
        userMap.put("name", user.getName());
        userMap.put("email", user.getEmail());
        userMap.put("role", user.getRole());
        
        return ResponseEntity.ok(Map.of("user", userMap));
    }

    @Data
    static class RegisterRequest {
        String name;
        String email;
        String password;
        String role;
        @JsonAlias({"class_code", "classCode"})
        String classCode;
    }

    @Data
    static class LoginRequest {
        String email;
        String password;
        String role;
    }
}
