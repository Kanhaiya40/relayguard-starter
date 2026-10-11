package com.relayguard.delivery;

import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;

@Service
public class DeliveryQueueService {
    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT id FROM delivery_jobs
                WHERE (state = 'PENDING' AND next_attempt_at <= CURRENT_TIMESTAMP)
                   OR (state = 'IN_PROGRESS' AND lease_until <= CURRENT_TIMESTAMP)
                ORDER BY next_attempt_at, created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE delivery_jobs job
            SET state = 'IN_PROGRESS',
                lease_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds',
                updated_at = CURRENT_TIMESTAMP
            FROM candidate
            WHERE job.id = candidate.id
            RETURNING job.id
            """;

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final DeliveryJobRepository jobs;
    private final DeliveryAttemptRepository attempts;
    private final DeliveryRetryPolicy retryPolicy;
    private final Counter successfulAttempts;
    private final Counter failedAttempts;
    private final Timer attemptDuration;

    public DeliveryQueueService(JdbcTemplate jdbc, EntityManager entityManager, DeliveryJobRepository jobs,
            DeliveryAttemptRepository attempts, DeliveryRetryPolicy retryPolicy, MeterRegistry meters) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.jobs = jobs;
        this.attempts = attempts;
        this.retryPolicy = retryPolicy;
        this.successfulAttempts = Counter.builder("relayguard.delivery.attempts").tag("outcome", "success").register(meters);
        this.failedAttempts = Counter.builder("relayguard.delivery.attempts").tag("outcome", "failure").register(meters);
        this.attemptDuration = Timer.builder("relayguard.delivery.duration").register(meters);
    }

    @Transactional
    public Optional<ClaimedDelivery> claimNext() {
        List<UUID> ids = jdbc.query(CLAIM_SQL, (result, row) -> result.getObject(1, UUID.class));
        if (ids.isEmpty()) return Optional.empty();

        DeliveryJob job = jobs.findById(ids.getFirst()).orElseThrow();
        attempts.findFirstByJob_IdAndCompletedAtIsNullOrderByAttemptNumberDesc(job.getId())
                .ifPresent(previous -> previous.complete(Instant.now(), 0, null, "WORKER_LOST", "WORKER_LOST"));
        Instant now = Instant.now();
        job.markInProgress(now.plusSeconds(60));
        DeliveryAttempt attempt = attempts.saveAndFlush(new DeliveryAttempt(job, job.getAttemptCount(), now));
        entityManager.flush();

        var event = job.getEvent();
        return Optional.of(new ClaimedDelivery(job.getId(), attempt.getId(), job.getAttemptCount(),
                event.getId(), event.getEventType(), event.getPayloadJson(), event.getReceivedAt(),
                job.getDestination().getTargetUrl()));
    }

    @Transactional
    public void complete(ClaimedDelivery claim, DeliveryResult result) {
        DeliveryJob job = jobs.findById(claim.jobId()).orElseThrow();
        DeliveryAttempt attempt = attempts.findById(claim.attemptId()).orElseThrow();
        Instant now = Instant.now();
        attemptDuration.record(result.durationMs(), TimeUnit.MILLISECONDS);
        if (result.succeeded()) {
            successfulAttempts.increment();
            attempt.complete(now, result.durationMs(), result.httpStatus(), "DELIVERED", null);
            job.markDelivered(result.httpStatus());
            return;
        }

        failedAttempts.increment();

        String outcome = result.httpStatus() == null ? "NETWORK_ERROR" : "HTTP_ERROR";
        attempt.complete(now, result.durationMs(), result.httpStatus(), outcome, result.errorCode());
        if (retryPolicy.isPermanentHttpFailure(result.httpStatus())
                || job.getAttemptCount() >= retryPolicy.maxAttempts()) {
            job.markExhausted(result.httpStatus(), result.errorCode());
        } else {
            job.scheduleRetry(result.httpStatus(), result.errorCode(), retryPolicy.nextAttemptAt(job.getAttemptCount(), now));
        }
    }
}
