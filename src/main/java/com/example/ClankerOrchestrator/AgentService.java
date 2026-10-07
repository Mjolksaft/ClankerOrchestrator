package com.example.ClankerOrchestrator;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

@Service 
public class AgentService {

    private final Map<Long, Agent> agents = new ConcurrentHashMap<Long, Agent>();
    private final AtomicLong nextId = new AtomicLong(1);

    public Agent CreateAgent(String name) {
        if(name == null || name.isBlank()) {
            throw new IllegalArgumentException("Agent name is required");
        }

        Agent agent = new Agent(nextId.getAndIncrement(), name.trim());
        agents.put(agent.getId(), agent);

        return agent;
    }

    public List<Agent> GetAgents() {
        return agents.values().stream()
            .sorted(Comparator.comparingLong(Agent::getId))
            .toList();
    }

    public Agent GetAgent(long id) {
        Agent agent = agents.get(id);
        if(agent == null) {
            throw new NoSuchElementException("No agent with id " + id);
        }

        return agent;
    }

    public void DeleteAgent(long id) {
        if(agents.remove(id) == null) {
            throw new NoSuchElementException("No agent with id " + id);
        }
    }

    public void Like(long id, String item) {
        GetAgent(id).Like(Require(item));
    }

    public void Dislike(long id, String item) {
        GetAgent(id).Dislike(Require(item));
    }

    public boolean Unlike(long id, String item) {
        return GetAgent(id).Unlike(Require(item));
    }

    public boolean Undislike(long id, String item) {
        return GetAgent(id).Undislike(Require(item));
    }

    private String Require(String item) {
        if(item == null || item.isBlank()) {
            throw new IllegalArgumentException("Preference item is required");
        }

        return item.trim();
    }
}