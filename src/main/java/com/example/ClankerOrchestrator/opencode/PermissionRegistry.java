package com.example.ClankerOrchestrator.opencode;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import com.example.ClankerOrchestrator.dto.PermissionDto;

@Component
public class PermissionRegistry {

    private final Map<String, Map<String, PermissionDto>> pending = new ConcurrentHashMap<String, Map<String, PermissionDto>>();

    public void Add(String sessionId, PermissionDto permission) {
        if(sessionId == null || sessionId.isBlank() || permission == null || permission.requestId() == null || permission.requestId().isBlank()) {
            return;
        }

        pending.computeIfAbsent(sessionId, key -> new ConcurrentHashMap<String, PermissionDto>())
            .put(permission.requestId(), permission);
    }

    public List<PermissionDto> Pending(String sessionId) {
        if(sessionId == null || sessionId.isBlank()) {
            return List.of();
        }

        Map<String, PermissionDto> session = pending.get(sessionId);
        if(session == null) {
            return List.of();
        }

        return List.copyOf(session.values());
    }

    public PermissionDto Find(String sessionId, String requestId) {
        if(sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            return null;
        }

        Map<String, PermissionDto> session = pending.get(sessionId);

        return session == null ? null : session.get(requestId);
    }

    public void Remove(String sessionId, String requestId) {
        if(sessionId == null || sessionId.isBlank() || requestId == null || requestId.isBlank()) {
            return;
        }

        Map<String, PermissionDto> session = pending.get(sessionId);
        if(session == null) {
            return;
        }

        session.remove(requestId);

        if(session.isEmpty()) {
            pending.remove(sessionId, session);
        }
    }

    public void Clear(String sessionId) {
        if(sessionId == null || sessionId.isBlank()) {
            return;
        }

        pending.remove(sessionId);
    }
}
