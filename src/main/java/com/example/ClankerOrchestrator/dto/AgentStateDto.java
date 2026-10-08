package com.example.ClankerOrchestrator.dto;

import java.util.List;

import com.example.ClankerOrchestrator.agent.AgentState;

public record AgentStateDto(long agentId, String sessionId, AgentState state, List<PermissionDto> permissions) {
}
