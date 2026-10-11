package com.relayguard.delivery;

import java.time.Instant;
import java.util.UUID;

public record DestinationResponse(UUID id, UUID projectId, String url, boolean enabled, Instant createdAt,
        int retryMaxAttempts, long retryBaseDelayMs, long retryMaxDelayMs) {
    static DestinationResponse from(WebhookDestination destination) {
        return new DestinationResponse(destination.getId(), destination.getProject().getId(),
                destination.getTargetUrl(), destination.isEnabled(), destination.getCreatedAt(),
                destination.getRetryMaxAttempts(), destination.getRetryBaseDelayMs(), destination.getRetryMaxDelayMs());
    }
}
