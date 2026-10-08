package com.example.ClankerOrchestrator.dto;

import java.util.List;

public record CreateAgentRequest(String name, List<String> likes, List<String> dislikes) {
}