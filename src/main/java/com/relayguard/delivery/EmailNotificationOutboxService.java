package com.relayguard.delivery;

import com.relayguard.identity.Project;
import jakarta.persistence.EntityManager;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailNotificationOutboxService {
    private static final List<String> FAILURE_OUTCOMES = List.of("HTTP_ERROR", "NETWORK_ERROR", "WORKER_LOST");
    private static final String CLAIM_SQL = """
            WITH candidate AS (
                SELECT id FROM email_notification_outbox
                WHERE (status = 'PENDING' AND available_at <= CURRENT_TIMESTAMP)
                   OR (status = 'IN_PROGRESS' AND lease_until <= CURRENT_TIMESTAMP)
                ORDER BY available_at, created_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            UPDATE email_notification_outbox entry
            SET status = 'IN_PROGRESS',
                attempt_count = entry.attempt_count + 1,
                lease_until = CURRENT_TIMESTAMP + INTERVAL '60 seconds'
            FROM candidate
            WHERE entry.id = candidate.id
            RETURNING entry.id
            """;

    private final ProjectNotificationSettingsRepository settings;
    private final DeliveryAttemptRepository attempts;
    private final EmailNotificationOutboxRepository outbox;
    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;
    private final boolean emailEnabled;

    public EmailNotificationOutboxService(ProjectNotificationSettingsRepository settings,
            DeliveryAttemptRepository attempts, EmailNotificationOutboxRepository outbox,
            JdbcTemplate jdbc, EntityManager entityManager,
            @Value("${relayguard.notifications.email.enabled:false}") boolean emailEnabled) {
        this.settings = settings;
        this.attempts = attempts;
        this.outbox = outbox;
        this.jdbc = jdbc;
        this.entityManager = entityManager;
        this.emailEnabled = emailEnabled;
    }

    @Transactional
    public void recordFailure(DeliveryJob job, String failureCode, Instant now) {
        Project project = job.getDestination().getProject();
        var config = settings.findByProjectId(project.getId()).orElse(null);
        if (!emailEnabled || config == null || !config.isEnabled()) return;

        Instant since = now.minusSeconds(config.getWindowMinutes() * 60L);
        long failureCount = attempts.countByJob_Destination_Project_IdAndJob_Destination_IdAndCompletedAtGreaterThanEqualAndOutcomeIn(
                project.getId(), job.getDestination().getId(), since, FAILURE_OUTCOMES);
        if (failureCount < config.getFailureThreshold()) return;

        long cooldownSeconds = config.getCooldownMinutes() * 60L;
        long bucket = Math.floorDiv(now.getEpochSecond(), cooldownSeconds);
        String deduplicationKey = project.getId() + ":" + job.getDestination().getId() + ":" + bucket;
        jdbc.update("""
                INSERT INTO email_notification_outbox
                    (id, project_id, event_id, destination_id, recipient_email, failure_count,
                     failure_code, deduplication_key, status, attempt_count, available_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (deduplication_key) DO NOTHING
                """, UUID.randomUUID(), project.getId(), job.getEvent().getId(), job.getDestination().getId(),
                config.getRecipientEmail(), (int) Math.min(failureCount, Integer.MAX_VALUE),
                safeFailureCode(failureCode), deduplicationKey,
                OffsetDateTime.ofInstant(now, ZoneOffset.UTC), OffsetDateTime.ofInstant(now, ZoneOffset.UTC));
    }

    @Transactional
    public Optional<EmailNotificationMessage> claimNext() {
        List<UUID> ids = jdbc.query(CLAIM_SQL, (result, row) -> result.getObject(1, UUID.class));
        if (ids.isEmpty()) return Optional.empty();
        EmailNotificationOutboxEntry entry = outbox.findById(ids.getFirst()).orElseThrow();
        entityManager.flush();
        String destinationHost = URI.create(entry.getDestination().getTargetUrl()).getHost();
        if (destinationHost == null) destinationHost = "unknown";
        return Optional.of(new EmailNotificationMessage(entry.getId(), entry.getRecipientEmail(),
                entry.getProject().getId(), entry.getProject().getName(), entry.getEvent().getId(),
                destinationHost, entry.getFailureCount(), entry.getFailureCode(), entry.getAttemptCount()));
    }

    @Transactional
    public void markSent(UUID id, Instant now) {
        outbox.findById(id).orElseThrow().markSent(now);
    }

    @Transactional
    public void retryOrFail(UUID id, int attemptCount, Instant now) {
        EmailNotificationOutboxEntry entry = outbox.findById(id).orElseThrow();
        if (attemptCount >= 8) {
            entry.markFailed("EMAIL_SEND_FAILED");
            return;
        }
        long delaySeconds = Math.min(3600, 30L << Math.min(attemptCount - 1, 7));
        entry.retryAt(now.plusSeconds(delaySeconds), "EMAIL_SEND_FAILED");
    }

    @Transactional
    public void deleteForEvent(UUID eventId) {
        outbox.deleteByEvent_Id(eventId);
    }

    private String safeFailureCode(String code) {
        if (code == null || !code.matches("[A-Z0-9_]{1,50}")) return "DELIVERY_FAILED";
        return code;
    }
}
