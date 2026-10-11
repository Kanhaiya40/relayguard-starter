package com.relayguard.identity;

import java.util.UUID;

public record WorkspaceResponse(UUID id, String name) {
    static WorkspaceResponse from(Workspace workspace) {
        return new WorkspaceResponse(workspace.getId(), workspace.getName());
    }
}
