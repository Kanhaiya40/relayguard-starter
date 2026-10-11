package com.relayguard.event;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record EventDetailResponse(UUID id, String eventType, String status, Instant receivedAt, JsonNode payload) {}
