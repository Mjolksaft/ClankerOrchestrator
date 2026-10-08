package com.example.ClankerOrchestrator.agent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

@Component
public class AgentSessionState {
    private final Map<String, AgentState> states = new ConcurrentHashMap<String, AgentState>();
    private final Map<String, Long> agentBySession = new ConcurrentHashMap<String, Long>();

    public void SetStateForSession(String sessionId, AgentState state) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        states.put(sessionId, state);
    }

    public AgentState GetStateForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return AgentState.IDLE;
        }
        return states.getOrDefault(sessionId, AgentState.IDLE);
    }

    public void SetAgentForSession(String sessionId, long agentId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        agentBySession.put(sessionId, agentId);
    }

    public Long GetAgentForSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return null;
        }
        return agentBySession.get(sessionId);
    }

    public void ClearSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }
        states.remove(sessionId);
        agentBySession.remove(sessionId);
    }
}
