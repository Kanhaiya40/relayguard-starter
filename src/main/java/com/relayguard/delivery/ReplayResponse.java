package com.relayguard.delivery;

import java.util.List;
import java.util.UUID;

public record ReplayResponse(UUID replayId, UUID eventId, List<UUID> deliveryJobIds, boolean replayed) {}
