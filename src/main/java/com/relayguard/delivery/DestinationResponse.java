package com.relayguard.delivery;

import java.time.Instant;
import java.util.UUID;

public record DestinationResponse(UUID id, UUID projectId, String url, boolean enabled, Instant createdAt) {
    static DestinationResponse from(WebhookDestination destination) {
        return new DestinationResponse(destination.getId(), destination.getProject().getId(),
                destination.getTargetUrl(), destination.isEnabled(), destination.getCreatedAt());
    }
}
