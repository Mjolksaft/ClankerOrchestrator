package com.example.ClankerOrchestrator.opencode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.ClankerOrchestrator.agent.AgentSessionState;
import com.example.ClankerOrchestrator.agent.AgentState;
import com.example.ClankerOrchestrator.dto.PermissionDto;

import tools.jackson.databind.json.JsonMapper;

class OpenCodeEventListenerTests {

    private JsonMapper mapper;
    private AgentSessionState agentSessionState;
    private PermissionRegistry permissionRegistry;
    private OpenCodeEventListener listener;

    @BeforeEach
    void SetUp() {
        mapper = JsonMapper.builder().build();
        agentSessionState = new AgentSessionState();
        permissionRegistry = new PermissionRegistry();
        listener = new OpenCodeEventListener(
            new EventStreamClient(mapper, "127.0.0.1", 4096, ""),
            agentSessionState,
            permissionRegistry
        );
    }

    private void Fire(String json) {
        listener.handleEvent(mapper.readTree(json));
    }

    @Test
    void VersionTwoAskRegistersPendingPermissionAndNeedsPermissionState() {
        Fire("{\"type\":\"permission.v2.asked\",\"properties\":{"
            + "\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"action\":\"bash\",\"resources\":[\"rm -rf /tmp/demo\"]}}");

        List<PermissionDto> pending = permissionRegistry.Pending("ses_1");
        assertEquals(1, pending.size());
        assertEquals("per_1", pending.get(0).requestId());
        assertEquals("bash", pending.get(0).permission());
        assertEquals(List.of("rm -rf /tmp/demo"), pending.get(0).resources());
        assertEquals(AgentState.NEEDS_PERMISSION, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void VersionOneAskReadsPatternsAndMetadataDescription() {
        Fire("{\"type\":\"permission.asked\",\"properties\":{"
            + "\"id\":\"per_2\",\"sessionID\":\"ses_1\",\"permission\":\"edit\","
            + "\"patterns\":[\"*.md\"],\"metadata\":{\"description\":\"Edit README.md\"}}}");

        List<PermissionDto> pending = permissionRegistry.Pending("ses_1");
        assertEquals(1, pending.size());
        assertEquals("edit", pending.get(0).permission());
        assertEquals(List.of("*.md"), pending.get(0).resources());
        assertEquals("Edit README.md", pending.get(0).description());
        assertEquals(AgentState.NEEDS_PERMISSION, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void RepliedEventClearsPermissionAndReturnsToWaiting() {
        Fire("{\"type\":\"permission.asked\",\"properties\":{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"permission\":\"bash\",\"patterns\":[\"*\"]}}");

        Fire("{\"type\":\"permission.replied\",\"properties\":{\"sessionID\":\"ses_1\",\"requestID\":\"per_1\",\"reply\":\"once\"}}");

        assertTrue(permissionRegistry.Pending("ses_1").isEmpty());
        assertEquals(AgentState.WAITING, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void RepliedEventKeepsStateWhenOtherPermissionsAreStillPending() {
        Fire("{\"type\":\"permission.asked\",\"properties\":{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"permission\":\"bash\",\"patterns\":[\"*\"]}}");
        Fire("{\"type\":\"permission.asked\",\"properties\":{\"id\":\"per_2\",\"sessionID\":\"ses_1\",\"permission\":\"edit\",\"patterns\":[\"*.md\"]}}");

        Fire("{\"type\":\"permission.replied\",\"properties\":{\"sessionID\":\"ses_1\",\"requestID\":\"per_1\",\"reply\":\"once\"}}");

        assertEquals(1, permissionRegistry.Pending("ses_1").size());
        assertEquals("per_2", permissionRegistry.Pending("ses_1").get(0).requestId());
        assertEquals(AgentState.NEEDS_PERMISSION, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void AskEventWithoutSessionIdIsIgnored() {
        Fire("{\"type\":\"permission.asked\",\"properties\":{\"id\":\"per_1\",\"permission\":\"bash\"}}");

        assertTrue(permissionRegistry.Pending("ses_1").isEmpty());
    }

    @Test
    void UnrelatedPermissionEventDoesNotCreatePendingRequest() {
        Fire("{\"type\":\"permission.saved.removed\",\"properties\":{\"id\":\"rule_1\"}}");

        assertTrue(permissionRegistry.Pending("ses_1").isEmpty());
    }

    @Test
    void OtherEventsStillTrackState() {
        Fire("{\"type\":\"message.part.updated\",\"properties\":{\"sessionID\":\"ses_1\",\"part\":{\"type\":\"text\"}}}");

        assertEquals(AgentState.WRITING, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void PayloadWrappedEventIsUnwrapped() {
        Fire("{\"directory\":\"D:\\\\code\",\"payload\":{\"id\":\"evt_1\",\"type\":\"permission.asked\","
            + "\"properties\":{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"permission\":\"external_directory\","
            + "\"patterns\":[\"D:\\\\code\\\\*\"],\"metadata\":{\"filepath\":\"D:\\\\code\\\\hello.txt\"}}}}");

        List<PermissionDto> pending = permissionRegistry.Pending("ses_1");
        assertEquals(1, pending.size());
        assertEquals("external_directory", pending.get(0).permission());
        assertEquals("D:\\code\\hello.txt", pending.get(0).description());
        assertEquals(AgentState.NEEDS_PERMISSION, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void PayloadWrappedRepliedEventClearsPermission() {
        Fire("{\"payload\":{\"type\":\"permission.asked\",\"properties\":{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"permission\":\"bash\",\"patterns\":[\"*\"]}}}");
        Fire("{\"payload\":{\"type\":\"permission.replied\",\"properties\":{\"sessionID\":\"ses_1\",\"requestID\":\"per_1\",\"reply\":\"once\"}}}");

        assertTrue(permissionRegistry.Pending("ses_1").isEmpty());
        assertEquals(AgentState.WAITING, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void SyncReplayEventsAreIgnored() {
        Fire("{\"payload\":{\"type\":\"sync\",\"syncEvent\":{\"type\":\"permission.asked.1\",\"seq\":5,"
            + "\"data\":{\"id\":\"per_1\",\"sessionID\":\"ses_1\",\"permission\":\"bash\",\"patterns\":[\"*\"]}}}}");

        assertTrue(permissionRegistry.Pending("ses_1").isEmpty());
    }

    @Test
    void PayloadWrappedPartEventsTrackState() {
        Fire("{\"payload\":{\"type\":\"message.part.updated\",\"properties\":{\"sessionID\":\"ses_1\",\"part\":{\"type\":\"tool\"}}}}");

        assertEquals(AgentState.THINKING, agentSessionState.GetStateForSession("ses_1"));

        Fire("{\"payload\":{\"type\":\"message.part.delta\",\"properties\":{\"sessionID\":\"ses_1\",\"delta\":\"hi\"}}}");

        assertEquals(AgentState.WRITING, agentSessionState.GetStateForSession("ses_1"));
    }

    @Test
    void EventWithoutTypeIsIgnored() {
        Fire("{\"payload\":{\"id\":\"evt_1\"}}");
        Fire("{\"something\":\"else\"}");

        assertTrue(permissionRegistry.Pending("ses_1").isEmpty());
    }
}
