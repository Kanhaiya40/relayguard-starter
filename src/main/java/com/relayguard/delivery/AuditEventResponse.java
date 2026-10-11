package com.relayguard.delivery;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(UUID id, String action, String targetType, UUID targetId, Instant createdAt) {
    static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(event.getId(), event.getAction(), event.getTargetType(), event.getTargetId(), event.getCreatedAt());
    }
}
