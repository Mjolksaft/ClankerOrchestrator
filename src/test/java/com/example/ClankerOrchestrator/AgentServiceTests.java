package com.example.ClankerOrchestrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

class AgentServiceTests {

    @Test
    void CreateAgentAssignsIdsAndTrimsName() {
        AgentService agentService = new AgentService();

        Agent first = agentService.CreateAgent("  Clanker  ");
        Agent second = agentService.CreateAgent("Second");

        assertEquals("Clanker", first.getName());
        assertEquals(1, first.getId());
        assertEquals(2, second.getId());
    }

    @Test
    void CreateAgentRejectsBlankName() {
        AgentService agentService = new AgentService();

        assertThrows(IllegalArgumentException.class, () -> agentService.CreateAgent("   "));
        assertThrows(IllegalArgumentException.class, () -> agentService.CreateAgent(null));
    }

    @Test
    void LikeAndDislikeTrackSeparateLists() {
        AgentService agentService = new AgentService();
        long id = agentService.CreateAgent("Clanker").getId();

        agentService.Like(id, "eating");
        agentService.Like(id, "sleeping");
        agentService.Dislike(id, "mondays");

        Agent agent = agentService.GetAgent(id);
        assertEquals(List.of("eating", "sleeping"), agent.getLikes());
        assertEquals(List.of("mondays"), agent.getDislikes());
    }

    @Test
    void UnlikeAndUndislikeReportWhetherRemoved() {
        AgentService agentService = new AgentService();
        long id = agentService.CreateAgent("Clanker").getId();

        agentService.Like(id, "eating");
        agentService.Dislike(id, "mondays");

        assertTrue(agentService.Unlike(id, "eating"));
        assertFalse(agentService.Unlike(id, "eating"));
        assertTrue(agentService.Undislike(id, "mondays"));
        assertFalse(agentService.Undislike(id, "eating"));
    }

    @Test
    void PreferencesRejectBlankItems() {
        AgentService agentService = new AgentService();
        long id = agentService.CreateAgent("Clanker").getId();

        assertThrows(IllegalArgumentException.class, () -> agentService.Like(id, " "));
        assertThrows(IllegalArgumentException.class, () -> agentService.Dislike(id, null));
    }

    @Test
    void UnknownIdThrows() {
        AgentService agentService = new AgentService();

        assertThrows(NoSuchElementException.class, () -> agentService.GetAgent(42));
        assertThrows(NoSuchElementException.class, () -> agentService.DeleteAgent(42));
    }

    @Test
    void GetAgentsReturnsInsertionOrder() {
        AgentService agentService = new AgentService();

        agentService.CreateAgent("First");
        agentService.CreateAgent("Second");

        assertEquals(List.of("First", "Second"), agentService.GetAgents().stream().map(Agent::getName).toList());
    }
}