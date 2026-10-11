package com.relayguard.delivery;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryRetryPolicyTest {
    private final DeliveryRetryPolicy policy = new DeliveryRetryPolicy(4, 10, 80);

    @Test
    void appliesCappedExponentialBackoffWithJitter() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Duration first = Duration.between(now, policy.nextAttemptAt(1, now));
        Duration capped = Duration.between(now, policy.nextAttemptAt(20, now));
        assertTrue(first.compareTo(Duration.ofMillis(5)) >= 0);
        assertTrue(first.compareTo(Duration.ofMillis(10)) <= 0);
        assertTrue(capped.compareTo(Duration.ofMillis(40)) >= 0);
        assertTrue(capped.compareTo(Duration.ofMillis(80)) <= 0);
    }

    @Test
    void retriesServerAndSelectedRateLimitFailuresButStopsOnOtherClientErrors() {
        assertTrue(policy.isPermanentHttpFailure(400));
        assertTrue(policy.isPermanentHttpFailure(404));
        assertFalse(policy.isPermanentHttpFailure(408));
        assertFalse(policy.isPermanentHttpFailure(429));
        assertFalse(policy.isPermanentHttpFailure(500));
        assertFalse(policy.isPermanentHttpFailure(null));
    }
}
