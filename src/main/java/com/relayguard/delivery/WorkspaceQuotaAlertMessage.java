package com.relayguard.delivery;

import java.time.LocalDate;
import java.util.UUID;

public record WorkspaceQuotaAlertMessage(UUID id, String recipientEmail, UUID workspaceId,
        String workspaceName, LocalDate periodStart, int usageCount, int attemptCount) {}
