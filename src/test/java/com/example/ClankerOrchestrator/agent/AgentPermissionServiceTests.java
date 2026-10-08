package com.example.ClankerOrchestrator.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ClankerOrchestrator.dto.PermissionDto;
import com.example.ClankerOrchestrator.dto.PermissionReplyRequest;
import com.example.ClankerOrchestrator.opencode.OpenCodeClient;
import com.example.ClankerOrchestrator.opencode.PermissionRegistry;
import com.example.ClankerOrchestrator.opencode.StubOpenCodeServer;

import tools.jackson.databind.json.JsonMapper;

class AgentPermissionServiceTests {

    private StubOpenCodeServer server;
    private AgentService agentService;
    private AgentSessionState agentSessionState;
    private PermissionRegistry permissionRegistry;
    private AgentPermissionService permissionService;
    private long agentId;

    @BeforeEach
    void SetUp() throws IOException {
        server = new StubOpenCodeServer();
        agentService = new AgentService();
        agentSessionState = new AgentSessionState();
        permissionRegistry = new PermissionRegistry();
        permissionService = new AgentPermissionService(
            agentService,
            agentSessionState,
            permissionRegistry,
            new OpenCodeClient(JsonMapper.builder().build(), "127.0.0.1", server.Port(), "", 10)
        );

        Agent agent = agentService.CreateAgent("Clanker");
        agent.setSessionID("ses_test");
        agentId = agent.getId();
        agentSessionState.SetStateForSession("ses_test", AgentState.NEEDS_PERMISSION);
    }

    @AfterEach
    void TearDown() {
        server.close();
    }

    private void PendingPermission(String requestId) {
        permissionRegistry.Add("ses_test", new PermissionDto(requestId, "bash", List.of("rm -rf /tmp/demo"), null));
    }

    @Test
    void ReplySendsOnceAndClearsPendingPermission() {
        PendingPermission("per_1");

        List<PermissionDto> remaining = permissionService.Reply(agentId, new PermissionReplyRequest(null, "once", null));

        assertTrue(remaining.isEmpty());
        assertTrue(permissionRegistry.Pending("ses_test").isEmpty());
        assertTrue(server.requests.contains("POST /permission/per_1/reply {\"reply\":\"once\"}"), server.requests.toString());
        assertEquals(AgentState.WAITING, agentSessionState.GetStateForSession("ses_test"));
    }

    @Test
    void ReplyTargetsTheRequestFromTheBody() {
        PendingPermission("per_1");
        PendingPermission("per_2");

        permissionService.Reply(agentId, new PermissionReplyRequest("per_2", "reject", "nope"));

        assertTrue(server.requests.contains("POST /permission/per_2/reply {\"reply\":\"reject\",\"message\":\"nope\"}"), server.requests.toString());
        assertEquals(1, permissionRegistry.Pending("ses_test").size());
        assertEquals("per_1", permissionRegistry.Pending("ses_test").get(0).requestId());
        assertEquals(AgentState.NEEDS_PERMISSION, agentSessionState.GetStateForSession("ses_test"));
    }

    @Test
    void ReplyFallsBackToFirstPendingWhenRequestIdIsMissing() {
        PendingPermission("per_first");

        permissionService.Reply(agentId, new PermissionReplyRequest("", "always", null));

        assertTrue(server.requests.contains("POST /permission/per_first/reply {\"reply\":\"always\"}"), server.requests.toString());
    }

    @Test
    void ReplyRejectsUnknownReplyValue() {
        PendingPermission("per_1");

        assertThrows(IllegalArgumentException.class, () -> permissionService.Reply(agentId, new PermissionReplyRequest(null, "maybe", null)));
        assertThrows(IllegalArgumentException.class, () -> permissionService.Reply(agentId, new PermissionReplyRequest(null, null, null)));
    }

    @Test
    void ReplyWithoutSessionThrowsNotFound() {
        Agent orphan = agentService.CreateAgent("Orphan");

        NoSuchElementException exception = assertThrows(
            NoSuchElementException.class,
            () -> permissionService.Reply(orphan.getId(), new PermissionReplyRequest(null, "once", null))
        );

        assertTrue(exception.getMessage().contains("session"), exception.getMessage());
    }

    @Test
    void ReplyWithoutPendingPermissionThrowsNotFound() {
        NoSuchElementException exception = assertThrows(
            NoSuchElementException.class,
            () -> permissionService.Reply(agentId, new PermissionReplyRequest(null, "once", null))
        );

        assertTrue(exception.getMessage().contains("permission"), exception.getMessage());
    }

    @Test
    void PendingReturnsRegisteredPermissions() {
        PendingPermission("per_1");

        List<PermissionDto> pending = permissionService.Pending(agentId);

        assertEquals(1, pending.size());
        assertEquals("bash", pending.get(0).permission());
        assertEquals(List.of("rm -rf /tmp/demo"), pending.get(0).resources());
    }

    @Test
    void PendingIsEmptyForAgentWithoutSession() {
        Agent orphan = agentService.CreateAgent("Orphan");

        assertTrue(permissionService.Pending(orphan.getId()).isEmpty());
    }
}
