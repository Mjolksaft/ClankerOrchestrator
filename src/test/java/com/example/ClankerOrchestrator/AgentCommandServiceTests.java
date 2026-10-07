package com.example.ClankerOrchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

class AgentCommandServiceTests {

    private StubOpenCodeServer server;
    private AgentService agentService;
    private OpenCodeClient openCodeClient;
    private AgentCommandService commandService;

    @BeforeEach
    void SetUp() throws IOException {
        server = new StubOpenCodeServer();
        agentService = new AgentService();
        openCodeClient = new OpenCodeClient(JsonMapper.builder().build(), "127.0.0.1", server.Port(), "", 10);
        commandService = new AgentCommandService(agentService, openCodeClient);
    }

    @AfterEach
    void TearDown() {
        server.close();
    }

    private long NewAgent() {
        return agentService.CreateAgent("Clanker").getId();
    }

    @Test
    void RunReturnsReplyAndToolsFromOpenCode() {
        server.SetReply("here is the code");

        CommandResult result = commandService.Run(NewAgent(), "write me a java class");

        assertEquals("here is the code", result.reply());
        assertEquals(List.of("bash", "write"), result.tools());
        assertEquals("write me a java class", result.prompt());
        assertNull(result.error());
    }

    @Test
    void RunCreatesOneSessionPerAgentAndReusesIt() {
        long id = NewAgent();

        CommandResult first = commandService.Run(id, "one");
        CommandResult second = commandService.Run(id, "two");

        assertEquals(first.sessionId(), second.sessionId());
        long sessions = server.requests.stream().filter(r -> r.startsWith("POST /session ")).count();
        assertEquals(1, sessions);
        assertTrue(server.requests.stream().anyMatch(r -> r.equals("POST /session {\"title\":\"Clanker\"}")), server.requests.toString());
    }

    @Test
    void RunUsesSeparateSessionsForSeparateAgents() {
        CommandResult first = commandService.Run(NewAgent(), "one");
        CommandResult second = commandService.Run(NewAgent(), "two");

        assertFalse(first.sessionId().equals(second.sessionId()));
    }

    @Test
    void RunSendsPromptAsTextPartAndOmitsPreferences() {
        long id = NewAgent();
        agentService.Like(id, "eating");
        agentService.Dislike(id, "mondays");

        commandService.Run(id, "build a parser");

        String sent = server.requests.stream().filter(r -> r.contains("/message")).findFirst().orElseThrow();
        assertFalse(sent.contains("eating"), sent);
        assertFalse(sent.contains("mondays"), sent);
    }

    @Test
    void RunRequiresRunningOpenCodeServer() {
        server.SetHealthy(false);

        OpenCodeUnavailableException exception = assertThrows(
            OpenCodeUnavailableException.class,
            () -> commandService.Run(NewAgent(), "hello")
        );
        assertTrue(exception.getMessage().contains("/OpenCode/start"), exception.getMessage());
    }

    @Test
    void RunRejectsBlankPrompt() {
        long id = NewAgent();

        assertThrows(IllegalArgumentException.class, () -> commandService.Run(id, "   "));
        assertThrows(IllegalArgumentException.class, () -> commandService.Run(id, null));
    }

    @Test
    void RunRejectsUnknownAgentBeforeCallingOpenCode() {
        long before = server.RequestCount();

        assertThrows(NoSuchElementException.class, () -> commandService.Run(42, "hello"));
        assertEquals(before, server.RequestCount());
    }

    @Test
    void IsHealthyReflectsServerState() {
        assertTrue(openCodeClient.IsHealthy());

        server.SetHealthy(false);
        assertFalse(openCodeClient.IsHealthy());
    }

    @Test
    void IsHealthyIsFalseWhenServerIsDown() throws IOException {
        StubOpenCodeServer stopped = new StubOpenCodeServer();
        int port = stopped.Port();
        stopped.close();

        OpenCodeClient dead = new OpenCodeClient(JsonMapper.builder().build(), "127.0.0.1", port, "", 2);
        assertFalse(dead.IsHealthy());
    }
}