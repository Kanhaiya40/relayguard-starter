package com.relayguard.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record WorkspaceUsageResponse(UUID workspaceId, LocalDate periodStart, Instant resetsAt,
        int acceptedEvents, int eventLimit, int remainingEvents, int warningThreshold,
        int maxRequestBytes, int maxDestinationsPerProject, int retentionDays) {}
