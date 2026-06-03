package com.smartshala.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartshala.entity.ClassEntity;
import com.smartshala.entity.User;
import com.smartshala.repository.ClassRepository;
import com.smartshala.repository.StudentClassRepository;
import com.smartshala.repository.UserRepository;
import com.smartshala.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class SignalingHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final Map<String, Map<String, WebSocketSession>> rooms = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final ClassRepository classRepository;
    private final StudentClassRepository studentClassRepository;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        User user = authenticate(session);
        if (user == null) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("Authentication required"));
            return;
        }
        session.getAttributes().put("userId", user.getId());
        session.getAttributes().put("name", user.getName());
        session.getAttributes().put("role", user.getRole());
        sessions.put(session.getId(), session);
        send(session, Map.of("type", "connected", "id", session.getId()));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        try {
            Map<String, Object> msg = mapper.readValue(message.getPayload(), new TypeReference<Map<String, Object>>() {});
            String type = (String) msg.get("type");

            if ("join".equals(type)) {
                joinRoom(session, msg);
            } else if ("signal".equals(type)) {
                forwardSignal(session, msg);
            } else if ("chat".equals(type)) {
                broadcastChat(session, msg);
            } else if ("end-class".equals(type)) {
                broadcastEndClass(session, msg);
            } else if ("media-state".equals(type)) {
                broadcastMediaState(session, msg);
            }
        } catch (Exception e) {
            send(session, Map.of("type", "error", "message", "Unable to process WebSocket message"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        leaveRoom(session);
        sessions.remove(session.getId());
    }

    private void joinRoom(WebSocketSession session, Map<String, Object> msg) throws IOException {
        String room = String.valueOf(msg.getOrDefault("room", "demo-room"));
        String classId = String.valueOf(msg.getOrDefault("classId", ""));
        if (!classId.isBlank() && !canViewClass(session, classId)) {
            send(session, Map.of("type", "error", "message", "You cannot join this live class"));
            return;
        }
        session.getAttributes().put("room", room);
        session.getAttributes().put("classId", classId);

        Map<String, WebSocketSession> roomSessions = rooms.computeIfAbsent(room, key -> new ConcurrentHashMap<>());
        Map<String, Map<String, String>> peers = new LinkedHashMap<>();
        for (WebSocketSession peer : roomSessions.values()) {
            peers.put(peer.getId(), peerInfo(peer));
        }

        roomSessions.put(session.getId(), session);
        send(session, Map.of("type", "peers", "peers", peers.values()));
        broadcastToRoom(room, Map.of("type", "peer-joined", "peer", peerInfo(session)), session.getId());
    }

    private void leaveRoom(WebSocketSession session) throws IOException {
        Object roomObj = session.getAttributes().get("room");
        if (roomObj == null) return;

        String room = String.valueOf(roomObj);
        Map<String, WebSocketSession> roomSessions = rooms.get(room);
        if (roomSessions == null) return;

        roomSessions.remove(session.getId());
        broadcastToRoom(room, Map.of("type", "peer-left", "id", session.getId()), session.getId());
        if (roomSessions.isEmpty()) rooms.remove(room);
    }

    private void forwardSignal(WebSocketSession sender, Map<String, Object> msg) throws IOException {
        String to = (String) msg.get("to");
        WebSocketSession target = sessions.get(to);
        Object senderRoom = sender.getAttributes().get("room");
        Object targetRoom = target == null ? null : target.getAttributes().get("room");
        if (target == null || !target.isOpen() || senderRoom == null || !senderRoom.equals(targetRoom)) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "signal");
        payload.put("from", sender.getId());
        payload.put("data", msg.get("data"));
        target.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
    }

    private void broadcastChat(WebSocketSession session, Map<String, Object> msg) throws IOException {
        Object roomObj = session.getAttributes().get("room");
        if (roomObj == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "chat");
        payload.put("from", session.getId());
        payload.put("name", session.getAttributes().getOrDefault("name", "Participant"));
        payload.put("role", session.getAttributes().getOrDefault("role", "student"));
        payload.put("message", msg.getOrDefault("message", ""));
        payload.put("sentAt", System.currentTimeMillis());
        broadcastToRoom(String.valueOf(roomObj), payload, null);
    }

    private void broadcastEndClass(WebSocketSession session, Map<String, Object> msg) throws IOException {
        Object roomObj = session.getAttributes().get("room");
        if (roomObj == null) return;
        // Only teachers/admins can broadcast end-class
        String role = String.valueOf(session.getAttributes().getOrDefault("role", "student"));
        if (!"teacher".equals(role) && !"admin".equals(role)) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "end-class");
        payload.put("from", session.getId());
        payload.put("name", session.getAttributes().getOrDefault("name", "Teacher"));
        payload.put("classId", msg.getOrDefault("classId", ""));
        broadcastToRoom(String.valueOf(roomObj), payload, session.getId());
    }

    private void broadcastMediaState(WebSocketSession session, Map<String, Object> msg) throws IOException {
        Object roomObj = session.getAttributes().get("room");
        if (roomObj == null) return;

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", "media-state");
        payload.put("from", session.getId());
        payload.put("audio", msg.getOrDefault("audio", true));
        payload.put("video", msg.getOrDefault("video", true));
        broadcastToRoom(String.valueOf(roomObj), payload, session.getId());
    }

    private void broadcastToRoom(String room, Map<String, ?> payload, String exceptSessionId) throws IOException {
        Map<String, WebSocketSession> roomSessions = rooms.get(room);
        if (roomSessions == null) return;

        String json = mapper.writeValueAsString(payload);
        for (WebSocketSession peer : roomSessions.values()) {
            if (exceptSessionId != null && exceptSessionId.equals(peer.getId())) continue;
            if (peer.isOpen()) peer.sendMessage(new TextMessage(json));
        }
    }

    private void send(WebSocketSession session, Map<String, ?> payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(mapper.writeValueAsString(payload)));
        }
    }

    private Map<String, String> peerInfo(WebSocketSession session) {
        return Map.of(
                "id", session.getId(),
                "name", String.valueOf(session.getAttributes().getOrDefault("name", "Participant")),
                "role", String.valueOf(session.getAttributes().getOrDefault("role", "student"))
        );
    }

    private User authenticate(WebSocketSession session) {
        try {
            String token = tokenFromQuery(session);
            if (token == null || token.isBlank()) return null;
            String userId = jwtUtil.extractId(token);
            return userRepository.findById(userId)
                    .filter(user -> jwtUtil.validateToken(token, user.getId()))
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }

    private String tokenFromQuery(WebSocketSession session) {
        if (session.getUri() == null || session.getUri().getRawQuery() == null) return null;
        for (String pair : session.getUri().getRawQuery().split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "token".equals(parts[0])) {
                return URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private boolean canViewClass(WebSocketSession session, String classId) {
        String userId = String.valueOf(session.getAttributes().get("userId"));
        String role = String.valueOf(session.getAttributes().get("role"));
        ClassEntity cls = classRepository.findById(classId).orElse(null);
        if (cls == null) return false;
        return "admin".equals(role)
                || userId.equals(cls.getTeacherId())
                || studentClassRepository.findByStudentIdAndClassId(userId, classId).isPresent();
    }
}
