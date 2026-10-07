package com.example.ClankerOrchestrator;

import java.util.List;

import org.springframework.stereotype.Service;

@Service 
public class AgentCommandService {

    private final AgentService agentService;
    private final OpenCodeClient openCodeClient;

    public AgentCommandService(AgentService agentService, OpenCodeClient openCodeClient) {
        this.agentService = agentService;
        this.openCodeClient = openCodeClient;
    }

    public CommandResult Run(long id, String prompt) {
        if(prompt == null || prompt.isBlank()) {
            throw new IllegalArgumentException("Prompt is required");
        }

        Agent agent = agentService.GetAgent(id);
        RequireServer();

        String text = prompt.trim();
        String sessionID = Session(agent);
        OpenCodeClient.PromptReply reply = openCodeClient.Prompt(sessionID, text);

        List<String> tools = reply.tools().isEmpty() ? openCodeClient.Tools(sessionID) : reply.tools();

        return new CommandResult(id, sessionID, text, reply.text(), List.copyOf(tools), reply.error());
    }

    public String Session(Agent agent) {
        if(agent.getSessionID() != null) {
            return agent.getSessionID();
        }

        String sessionID = openCodeClient.CreateSession(agent.getName());
        agent.setSessionID(sessionID);

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