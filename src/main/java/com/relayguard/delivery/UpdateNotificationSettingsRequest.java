package com.relayguard.delivery;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateNotificationSettingsRequest(
        boolean enabled,
        @NotBlank @Email @Size(max = 320) String recipientEmail,
        @Min(1) @Max(100) int failureThreshold,
        @Min(1) @Max(1440) int windowMinutes,
        @Min(1) @Max(10080) int cooldownMinutes) {}
