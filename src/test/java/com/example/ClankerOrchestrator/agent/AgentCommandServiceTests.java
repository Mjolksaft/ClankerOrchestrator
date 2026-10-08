package com.example.ClankerOrchestrator.agent;

import com.example.ClankerOrchestrator.dto.CommandResult;
import com.example.ClankerOrchestrator.opencode.OpenCodeClient;
import com.example.ClankerOrchestrator.opencode.StubOpenCodeServer;
import com.example.ClankerOrchestrator.exception.AgentBusyException;
import com.example.ClankerOrchestrator.exception.OpenCodeUnavailableException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

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
        commandService = new AgentCommandService(agentService, openCodeClient, new AgentSessionState());
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

    @Test
    void SecondCommandForSameAgentWhileRunningIsRejected() throws Exception {
        server.SetMessageDelay(400);
        long id = NewAgent();
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<CommandResult> first = pool.submit(() -> commandService.Run(id, "first"));

            AwaitMessageRequest();

            AgentBusyException busy = assertThrows(AgentBusyException.class, () -> commandService.Run(id, "second"));
            assertTrue(busy.getMessage().contains("already running"));

            assertEquals("hello", first.get(5, TimeUnit.SECONDS).reply());

            CommandResult after = commandService.Run(id, "third");
            assertEquals("hello", after.reply());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void DifferentAgentsRunConcurrently() throws Exception {
        server.SetMessageDelay(300);
        long firstAgent = NewAgent();
        long secondAgent = NewAgent();
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<CommandResult> first = pool.submit(() -> commandService.Run(firstAgent, "one"));
            Future<CommandResult> second = pool.submit(() -> commandService.Run(secondAgent, "two"));

            assertEquals("hello", first.get(5, TimeUnit.SECONDS).reply());
            assertEquals("hello", second.get(5, TimeUnit.SECONDS).reply());
        } finally {
            pool.shutdownNow();
        }
    }

    private void AwaitMessageRequest() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 5000;
        while(System.currentTimeMillis() < deadline) {
            for(String request : server.requests) {
                if(request.contains("/message")) {
                    return;
                }
            }
            Thread.sleep(10);
        }
        throw new AssertionError("opencode never received a message request");
    }
}