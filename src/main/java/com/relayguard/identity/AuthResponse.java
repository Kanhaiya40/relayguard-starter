package com.relayguard.identity;

import java.util.UUID;
import java.time.Instant;

public record AuthResponse(UUID userId, UUID workspaceId, String workspaceName,
        UUID accessTokenId, String accessToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "AuthResponse[userId=" + userId + ", workspaceId=" + workspaceId
                + ", workspaceName=" + workspaceName + ", accessTokenId=" + accessTokenId
                + ", accessToken=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
