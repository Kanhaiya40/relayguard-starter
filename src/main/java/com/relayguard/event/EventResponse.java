package com.relayguard.event;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(UUID id, String eventType, String status, Instant receivedAt) {
    static EventResponse from(WebhookEvent event) {
        return new EventResponse(event.getId(), event.getEventType(), event.getStatus().name(), event.getReceivedAt());
    }
}
