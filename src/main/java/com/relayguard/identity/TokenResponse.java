package com.relayguard.identity;

import java.util.UUID;
import java.time.Instant;

public record TokenResponse(UUID credentialId, String accessToken, Instant expiresAt) {
    @Override
    public String toString() {
        return "TokenResponse[credentialId=" + credentialId + ", accessToken=[REDACTED], expiresAt=" + expiresAt + "]";
    }
}
