package com.example.ClankerOrchestrator.agent;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;

import org.springframework.stereotype.Service;

import com.example.ClankerOrchestrator.dto.PermissionDto;
import com.example.ClankerOrchestrator.dto.PermissionReplyRequest;
import com.example.ClankerOrchestrator.opencode.OpenCodeClient;
import com.example.ClankerOrchestrator.opencode.PermissionRegistry;

@Service
public class AgentPermissionService {

    private static final Set<String> REPLIES = Set.of("once", "always", "reject");

    private final AgentService agentService;
    private final AgentSessionState agentSessionState;
    private final PermissionRegistry permissionRegistry;
    private final OpenCodeClient openCodeClient;

    public AgentPermissionService(
        AgentService agentService,
        AgentSessionState agentSessionState,
        PermissionRegistry permissionRegistry,
        OpenCodeClient openCodeClient
    ) {
        this.agentService = agentService;
        this.agentSessionState = agentSessionState;
        this.permissionRegistry = permissionRegistry;
        this.openCodeClient = openCodeClient;
    }

    public List<PermissionDto> Pending(long agentId) {
        Agent agent = agentService.GetAgent(agentId);

        return permissionRegistry.Pending(agent.getSessionID());
    }

    public List<PermissionDto> Reply(long agentId, PermissionReplyRequest request) {
        if(request == null || request.reply() == null || !REPLIES.contains(request.reply().trim())) {
            throw new IllegalArgumentException("reply must be one of once, always or reject");
        }

        String reply = request.reply().trim();
        Agent agent = agentService.GetAgent(agentId);
        String sessionId = agent.getSessionID();

        if(sessionId == null || sessionId.isBlank()) {
            throw new NoSuchElementException("agent " + agentId + " has no opencode session yet");
        }

        String requestId = request.requestId();
        if(requestId == null || requestId.isBlank()) {
            List<PermissionDto> pending = permissionRegistry.Pending(sessionId);
            if(pending.isEmpty()) {
                throw new NoSuchElementException("agent " + agentId + " has no pending permission request");
            }
            requestId = pending.get(0).requestId();
        }

        openCodeClient.ReplyPermission(sessionId, requestId, reply, request.message());
        permissionRegistry.Remove(sessionId, requestId);

        List<PermissionDto> remaining = permissionRegistry.Pending(sessionId);
        if(remaining.isEmpty()) {
            agentSessionState.SetStateForSession(sessionId, AgentState.WAITING);
        }

        return remaining;
    }
}
