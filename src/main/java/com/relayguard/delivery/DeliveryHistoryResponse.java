package com.relayguard.delivery;

import java.util.List;
import java.util.UUID;

public record DeliveryHistoryResponse(UUID jobId, UUID destinationId, String destinationHost,
        DeliveryState state, int attemptCount, Integer lastHttpStatus, String lastErrorCode,
        List<DeliveryAttemptResponse> attempts) {}
