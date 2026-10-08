package com.example.ClankerOrchestrator.agent;

import com.example.ClankerOrchestrator.dto.CommandResult;
import com.example.ClankerOrchestrator.opencode.OpenCodeClient;
import com.example.ClankerOrchestrator.exception.AgentBusyException;
import com.example.ClankerOrchestrator.exception.OpenCodeUnavailableException;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service 
public class AgentCommandService {

    private final AgentService agentService;
    private final OpenCodeClient openCodeClient;
    private final AgentSessionState agentSessionState;
    private final Set<Long> running = ConcurrentHashMap.newKeySet();

    public AgentCommandService(AgentService agentService, OpenCodeClient openCodeClient, AgentSessionState agentSessionState) {
        this.agentService = agentService;
        this.openCodeClient = openCodeClient;
        this.agentSessionState = agentSessionState;
    }

    public CommandResult Run(long id, String prompt) {
        if(prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt is required");
        }

        Agent agent = agentService.GetAgent(id);
        RequireServer();

        if(!running.add(id)) {
            throw new AgentBusyException("Agent " + id + " is already running a command. Wait for it to finish.");
        }

        try {
            String text = prompt.trim();
            String sessionID = Session(agent);
            agentSessionState.SetAgentForSession(sessionID, id);
            agentSessionState.SetStateForSession(sessionID, AgentState.WAITING);
            OpenCodeClient.PromptReply reply = openCodeClient.Prompt(sessionID, text);

            List<String> tools = reply.tools().isEmpty() ? openCodeClient.Tools(sessionID) : reply.tools();

            agentSessionState.SetStateForSession(sessionID, reply.error() == null ? AgentState.FINISHED : AgentState.IDLE);

            return new CommandResult(id, sessionID, text, reply.text(), List.copyOf(tools), reply.error());
        } finally {
            running.remove(id);
        }
    }

    public String Session(Agent agent) {
        if(agent.getSessionID() != null) {
            return agent.getSessionID();
        }

        String sessionID = openCodeClient.CreateSession(agent.getName());
        agent.setSessionID(sessionID);
        agentSessionState.SetAgentForSession(sessionID, agent.getId());
        agentSessionState.SetStateForSession(sessionID, AgentState.IDLE);

        return sessionID;
    }

    private void RequireServer() {
        if(!openCodeClient.IsHealthy()) {
            throw new OpenCodeUnavailableException(
                "opencode server is not responding at " + openCodeClient.BaseUrl() + ". Start it with POST /OpenCode/start."
            );
        }
    }
}