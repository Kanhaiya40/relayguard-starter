package com.relayguard.delivery;

import java.time.Instant;
import java.util.UUID;

public record NotificationSettingsResponse(UUID projectId, boolean configured, boolean enabled, String recipientEmail,
        int failureThreshold, int windowMinutes, int cooldownMinutes, Instant updatedAt) {
    static NotificationSettingsResponse from(ProjectNotificationSettings settings) {
        return new NotificationSettingsResponse(settings.getProjectId(), true, settings.isEnabled(),
                settings.getRecipientEmail(), settings.getFailureThreshold(), settings.getWindowMinutes(),
                settings.getCooldownMinutes(), settings.getUpdatedAt());
    }

    static NotificationSettingsResponse defaults(UUID projectId) {
        return new NotificationSettingsResponse(projectId, false, false, "", 3, 10, 60, null);
    }
}
