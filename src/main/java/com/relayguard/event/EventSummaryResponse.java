package com.relayguard.event;

import java.time.Instant;
import java.util.UUID;

public record EventSummaryResponse(UUID id, String eventType, String status, Instant receivedAt) {
    static EventSummaryResponse from(WebhookEvent event) {
        return new EventSummaryResponse(event.getId(), event.getEventType(), event.getStatus().name(), event.getReceivedAt());
    }
}
