package com.example.ClankerOrchestrator.opencode;

import com.example.ClankerOrchestrator.agent.AgentSessionState;
import com.example.ClankerOrchestrator.agent.AgentState;
import com.example.ClankerOrchestrator.dto.PermissionDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import tools.jackson.databind.JsonNode;

@Component
public class OpenCodeEventListener {
    private final EventStreamClient eventStreamClient;
    private final AgentSessionState agentSessionState;
    private final PermissionRegistry permissionRegistry;

    private final Map<String, Long> lastActivity = new ConcurrentHashMap<String, Long>();

    public OpenCodeEventListener(EventStreamClient eventStreamClient, AgentSessionState agentSessionState, PermissionRegistry permissionRegistry) {
        this.eventStreamClient = eventStreamClient;
        this.agentSessionState = agentSessionState;
        this.permissionRegistry = permissionRegistry;
    }

    @PostConstruct
    public void init() {
        eventStreamClient.start(this::handleEvent);
    }

    void handleEvent(JsonNode event) {
        if (event == null || event.isMissingNode()) {
            return;
        }

        JsonNode node = event;
        if (event.has("payload")) {
            JsonNode payload = event.path("payload");
            if ("sync".equals(payload.path("type").asText())) {
                return;
            }
            node = payload;
        }

        String type = node.path("type").asText();
        JsonNode properties = node.path("properties");
        if (type.isBlank()) {
            return;
        }

        if (type.startsWith("permission")) {
            handlePermission(type, properties);
            return;
        }

        if ("message.part.updated".equals(type) || "message.part.created".equals(type)) {
            String sid = getSessionIdFromProperties(properties);
            if (sid == null) {
                return;
            }
            JsonNode part = properties.path("part");
            if (part.isMissingNode()) {
                part = properties.path("message").path("part");
            }
            if (!part.isMissingNode()) {
                String partType = part.path("type").asText();
                if ("thought".equals(partType) || "reasoning".equals(partType) || "tool_call".equals(partType) || "tool_call_start".equals(partType) || "tool".equals(partType)) {
                    agentSessionState.SetStateForSession(sid, AgentState.THINKING);
                } else if ("tool_result".equals(partType)) {
                    agentSessionState.SetStateForSession(sid, AgentState.WAITING);
                } else if ("text".equals(partType) || "text_delta".equals(partType) || "assistant".equals(partType)) {
                    agentSessionState.SetStateForSession(sid, AgentState.WRITING);
                }
            }
            return;
        }

        if ("message.updated".equals(type) || "message.created".equals(type) || "message.part.delta".equals(type)) {
            String sid = getSessionIdFromProperties(properties);
            if (sid != null) {
                agentSessionState.SetStateForSession(sid, AgentState.WRITING);
            }
            return;
        }

        if ("session.updated".equals(type) || "session.idle".equals(type)) {
            String sid = getSessionIdFromProperties(properties);
            if (sid != null) {
                agentSessionState.SetStateForSession(sid, AgentState.IDLE);
            }
            return;
        }
    }

    private void handlePermission(String type, JsonNode properties) {
        String sid = getSessionIdFromProperties(properties);

        boolean settled = type.contains("replied") || type.contains("responded")
            || type.contains("cancel") || type.contains("expired") || type.contains("resolved");

        if (settled) {
            if (sid == null) {
                return;
            }
            String requestId = Text(properties, "requestID", "requestId", "permissionID", "permissionId");
            if (requestId != null) {
                permissionRegistry.Remove(sid, requestId);
            } else {
                permissionRegistry.Clear(sid);
            }
            if (permissionRegistry.Pending(sid).isEmpty()) {
                agentSessionState.SetStateForSession(sid, AgentState.WAITING);
            }
            return;
        }

        boolean asked = type.contains("ask") || type.contains("request");
        String requestId = Text(properties, "id", "requestID", "requestId", "permissionID", "permissionId");

        if (!asked && requestId == null) {
            return;
        }

        if (sid == null) {
            return;
        }

        if (requestId != null) {
            permissionRegistry.Add(sid, ToPermission(requestId, properties));
        }

        agentSessionState.SetStateForSession(sid, AgentState.NEEDS_PERMISSION);
    }

    private PermissionDto ToPermission(String requestId, JsonNode properties) {
        String permission = FirstText(properties, "permission", "action", "title");
        List<String> resources = FirstList(properties, "patterns", "resources");

        String description = FirstText(properties.path("metadata"), "description", "title", "command", "summary", "filepath", "path", "file");
        if (description == null) {
            description = FirstText(properties, "description");
        }

        return new PermissionDto(requestId, permission, resources, description);
    }

    private String Text(JsonNode node, String... names) {
        if (node == null || node.isMissingNode()) {
            return null;
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isMissingNode() && !value.isNull()) {
                String text = value.asText();
                if (!text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private String FirstText(JsonNode node, String... names) {
        return Text(node, names);
    }

    private List<String> FirstList(JsonNode node, String... names) {
        if (node == null || node.isMissingNode()) {
            return List.of();
        }
        for (String name : names) {
            JsonNode value = node.path(name);
            if (!value.isArray()) {
                continue;
            }
            List<String> items = new ArrayList<String>();
            for (JsonNode item : value) {
                String text = item.asText();
                if (!text.isBlank()) {
                    items.add(text);
                }
            }
            if (!items.isEmpty()) {
                return items;
            }
        }
        return List.of();
    }

    private String getSessionIdFromProperties(JsonNode properties) {
        if (properties == null || properties.isMissingNode()) {
            return null;
        }
        JsonNode sid = properties.path("sessionID");
        if (sid.isMissingNode()) {
            sid = properties.path("sessionId");
        }
        if (sid.isMissingNode()) {
            sid = properties.path("session_id");
        }
        if (sid.isMissingNode()) {
            JsonNode message = properties.path("message");
            if (!message.isMissingNode()) {
                sid = message.path("sessionID");
                if (sid.isMissingNode()) {
                    sid = message.path("sessionId");
                }
            }
        }
        if (sid.isMissingNode()) {
            return null;
        }
        String s = sid.asText();
        return s.isBlank() ? null : s;
    }
}
