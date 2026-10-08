package com.example.ClankerOrchestrator.agent;

import com.example.ClankerOrchestrator.dto.AgentStateDto;
import com.example.ClankerOrchestrator.dto.CommandRequest;
import com.example.ClankerOrchestrator.dto.CommandResult;
import com.example.ClankerOrchestrator.dto.CreateAgentRequest;
import com.example.ClankerOrchestrator.exception.AgentBusyException;
import com.example.ClankerOrchestrator.exception.OpenCodeUnavailableException;
import com.example.ClankerOrchestrator.dto.PermissionDto;
import com.example.ClankerOrchestrator.dto.PermissionReplyRequest;
import com.example.ClankerOrchestrator.dto.PreferenceRequest;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/Agent")
public class AgentController {

    private final AgentService agentService;
    private final AgentCommandService agentCommandService;
    private final AgentSessionState agentSessionState;
    private final AgentPermissionService agentPermissionService;

    public AgentController(
            AgentService agentService,
            AgentCommandService agentCommandService,
            AgentSessionState agentSessionState,
            AgentPermissionService agentPermissionService) {
        this.agentService = agentService;
        this.agentCommandService = agentCommandService;
        this.agentSessionState = agentSessionState;
        this.agentPermissionService = agentPermissionService;
    }

    @PostMapping
    public ResponseEntity<Agent> CreateAgent(@RequestBody CreateAgentRequest request) {
        Agent agent = agentService.CreateAgent(request.name());

        if (request.likes() != null) {
            request.likes().forEach(item -> agentService.Like(agent.getId(), item));
        }

        if (request.dislikes() != null) {
            request.dislikes().forEach(item -> agentService.Dislike(agent.getId(), item));
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(agent);
    }

    @GetMapping
    public List<Agent> GetAgents() {
        return agentService.GetAgents();
    }

    @GetMapping("/{id}")
    public Agent GetAgent(@PathVariable long id) {
        return agentService.GetAgent(id);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> DeleteAgent(@PathVariable long id) {
        agentService.DeleteAgent(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/like")
    public Agent Like(@PathVariable long id, @RequestBody PreferenceRequest request) {
        agentService.Like(id, request.item());

        return agentService.GetAgent(id);
    }

    @PostMapping("/{id}/dislike")
    public Agent Dislike(@PathVariable long id, @RequestBody PreferenceRequest request) {
        agentService.Dislike(id, request.item());

        return agentService.GetAgent(id);
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<Void> Unlike(@PathVariable long id, @RequestBody PreferenceRequest request) {
        boolean removed = agentService.Unlike(id, request.item());

        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{id}/dislike")
    public ResponseEntity<Void> Undislike(@PathVariable long id, @RequestBody PreferenceRequest request) {
        boolean removed = agentService.Undislike(id, request.item());

        return removed ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    @PostMapping("/{id}/command")
    public CommandResult RunCommand(@PathVariable long id, @RequestBody CommandRequest request) {
        return agentCommandService.Run(id, request.command());
    }

    @GetMapping("/{id}/state")
    public ResponseEntity<AgentStateDto> GetState(@PathVariable long id) {
        Agent agent = agentService.GetAgent(id);
        AgentState state = agent.getState();
        if (agent.getSessionID() != null) {
            state = agentSessionState.GetStateForSession(agent.getSessionID());
            agent.setState(state);
        }
        return ResponseEntity
                .ok(new AgentStateDto(id, agent.getSessionID(), state, agentPermissionService.Pending(id)));
    }

    @GetMapping("/{id}/permission")
    public ResponseEntity<List<PermissionDto>> GetPermissions(@PathVariable long id) {
        return ResponseEntity.ok(agentPermissionService.Pending(id));
    }

    @PostMapping("/{id}/permission")
    public ResponseEntity<List<PermissionDto>> ReplyPermission(@PathVariable long id,
            @RequestBody PermissionReplyRequest request) {
        return ResponseEntity.ok(agentPermissionService.Reply(id, request));
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<String> HandleNoSuchElement(NoSuchElementException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> HandleIllegalArgument(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(exception.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<String> HandleIllegalState(IllegalStateException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }

    @ExceptionHandler(OpenCodeUnavailableException.class)
    public ResponseEntity<String> HandleOpenCodeUnavailable(OpenCodeUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(exception.getMessage());
    }

    @ExceptionHandler(AgentBusyException.class)
    public ResponseEntity<String> HandleAgentBusy(AgentBusyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(exception.getMessage());
    }
}