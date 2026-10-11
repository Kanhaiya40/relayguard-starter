package com.relayguard.delivery;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateDestinationRetryPolicyRequest(
        @Min(1) @Max(25) Integer retryMaxAttempts,
        @Min(1) @Max(86_400_000) Long retryBaseDelayMs,
        @Min(1) @Max(86_400_000) Long retryMaxDelayMs) {
    @AssertTrue(message = "all retry policy values are required and retryMaxDelayMs must be at least retryBaseDelayMs")
    public boolean isRetryPolicyValid() {
        return retryMaxAttempts != null && retryBaseDelayMs != null && retryMaxDelayMs != null
                && retryMaxDelayMs >= retryBaseDelayMs;
    }
}
