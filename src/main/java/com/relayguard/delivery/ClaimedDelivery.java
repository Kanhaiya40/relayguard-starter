package com.relayguard.delivery;

import java.time.Instant;
import java.util.UUID;

public record ClaimedDelivery(UUID jobId, UUID attemptId, int attemptNumber, UUID eventId,
        String eventType, String payloadJson, Instant receivedAt, String destinationUrl) {}
