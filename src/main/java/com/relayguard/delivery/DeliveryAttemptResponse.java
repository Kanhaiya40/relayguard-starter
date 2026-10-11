package com.relayguard.delivery;

import java.time.Instant;
import java.util.UUID;

public record DeliveryAttemptResponse(UUID id, int attemptNumber, Instant startedAt, Instant completedAt,
        Integer httpStatus, Long durationMs, String outcome, String errorCode) {
    static DeliveryAttemptResponse from(DeliveryAttempt attempt) {
        return new DeliveryAttemptResponse(attempt.getId(), attempt.getAttemptNumber(), attempt.getStartedAt(),
                attempt.getCompletedAt(), attempt.getHttpStatus(), attempt.getDurationMs(),
                attempt.getOutcome(), attempt.getErrorCode());
    }
}
