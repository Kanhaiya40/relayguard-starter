package com.relayguard.identity;

import java.time.Instant;
import java.util.UUID;

public record ProjectCredentialResponse(UUID id, String keyPrefix, String token, Instant createdAt) {
    static ProjectCredentialResponse from(IssuedProjectCredential issued) {
        ProjectCredential credential = issued.credential();
        return new ProjectCredentialResponse(credential.getId(), credential.getKeyPrefix(),
                issued.token(), credential.getCreatedAt());
    }

    @Override
    public String toString() {
        return "ProjectCredentialResponse[id=" + id + ", keyPrefix=" + keyPrefix
                + ", token=[REDACTED], createdAt=" + createdAt + "]";
    }
}
