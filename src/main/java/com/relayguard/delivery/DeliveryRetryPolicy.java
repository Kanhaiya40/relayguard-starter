package com.relayguard.delivery;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DeliveryRetryPolicy {
    private final int maxAttempts;
    private final Duration baseDelay;
    private final Duration maxDelay;

    public DeliveryRetryPolicy(
            @Value("${relayguard.delivery.max-attempts:8}") int maxAttempts,
            @Value("${relayguard.delivery.base-delay-ms:1000}") long baseDelayMs,
            @Value("${relayguard.delivery.max-delay-ms:3600000}") long maxDelayMs) {
        if (maxAttempts < 1 || maxAttempts > 25 || baseDelayMs < 1 || baseDelayMs > 86_400_000
                || maxDelayMs < baseDelayMs || maxDelayMs > 86_400_000) {
            throw new IllegalArgumentException("Invalid delivery retry policy configuration");
        }
        this.maxAttempts = maxAttempts;
        this.baseDelay = Duration.ofMillis(baseDelayMs);
        this.maxDelay = Duration.ofMillis(maxDelayMs);
    }

    public int maxAttempts() { return maxAttempts; }
    public long baseDelayMs() { return baseDelay.toMillis(); }
    public long maxDelayMs() { return maxDelay.toMillis(); }

    public boolean isPermanentHttpFailure(Integer status) {
        return status != null && status >= 300 && status < 500 && status != 408 && status != 429;
    }

    public Instant nextAttemptAt(int attemptNumber, Instant now) {
        return nextAttemptAt(attemptNumber, now, baseDelay.toMillis(), maxDelay.toMillis());
    }

    public Instant nextAttemptAt(int attemptNumber, Instant now, long baseDelayMs, long maxDelayMs) {
        long multiplier = 1L << Math.min(Math.max(attemptNumber - 1, 0), 30);
        long scaled = baseDelayMs > Long.MAX_VALUE / multiplier ? Long.MAX_VALUE : baseDelayMs * multiplier;
        long capped = Math.min(maxDelayMs, scaled);
        long lowerBound = Math.max(1, capped / 2);
        return now.plusMillis(ThreadLocalRandom.current().nextLong(lowerBound, capped + 1));
    }
}
