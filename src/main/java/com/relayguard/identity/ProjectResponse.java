package com.relayguard.identity;

import java.time.Instant;
import java.util.UUID;

public record ProjectResponse(UUID id, UUID workspaceId, String name, Instant createdAt) {
    static ProjectResponse from(Project project) {
        return new ProjectResponse(project.getId(), project.getWorkspace().getId(),
                project.getName(), project.getCreatedAt());
    }
}
