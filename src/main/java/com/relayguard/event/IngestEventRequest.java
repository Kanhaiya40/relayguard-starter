package com.relayguard.event;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record IngestEventRequest(
        @NotBlank @Size(max = 150) String eventType,
        @NotNull JsonNode payload) {}
