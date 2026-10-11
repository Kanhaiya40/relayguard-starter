package com.relayguard.delivery;

import java.util.UUID;

public record EmailNotificationMessage(UUID id, String recipientEmail, UUID projectId, String projectName,
        UUID eventId, String destinationHost, int failureCount, String failureCode, int attemptCount) {}
