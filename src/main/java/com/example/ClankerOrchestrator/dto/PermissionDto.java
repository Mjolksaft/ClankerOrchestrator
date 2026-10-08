package com.example.ClankerOrchestrator.dto;

import java.util.List;

public record PermissionDto(
    String requestId,
    String permission,
    List<String> resources,
    String description
) {
}
