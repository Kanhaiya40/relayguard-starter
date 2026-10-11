package com.relayguard.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record CreateDestinationRequest(@NotBlank @Size(max = 2048) String url,
        @Min(1) @Max(25) Integer retryMaxAttempts,
        @Min(1) @Max(86_400_000) Long retryBaseDelayMs,
        @Min(1) @Max(86_400_000) Long retryMaxDelayMs) {
    @jakarta.validation.constraints.AssertTrue(message = "retryMaxDelayMs must be at least retryBaseDelayMs")
    public boolean isRetryDelayRangeValid() {
        return retryBaseDelayMs == null || retryMaxDelayMs == null || retryMaxDelayMs >= retryBaseDelayMs;
    }
}
