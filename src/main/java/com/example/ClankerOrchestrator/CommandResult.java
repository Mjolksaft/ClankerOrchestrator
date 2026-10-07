package com.example.ClankerOrchestrator;

public record CommandResult(
    long agentId,
    String sessionId,
    String prompt,
    String reply,
    java.util.List<String> tools,
    String error
) {
}