package com.relayguard.delivery;

import com.relayguard.event.WebhookEvent;
import com.relayguard.identity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

@Entity
@Table(name = "email_notification_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uq_email_notification_deduplication_key", columnNames = "deduplication_key"))
public class EmailNotificationOutboxEntry {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WebhookEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private WebhookDestination destination;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "failure_count", nullable = false)
    private int failureCount;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "deduplication_key", nullable = false, length = 160)
    private String deduplicationKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EmailNotificationStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "available_at", nullable = false)
    private Instant availableAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    protected EmailNotificationOutboxEntry() {}

    public EmailNotificationOutboxEntry(Project project, WebhookEvent event, WebhookDestination destination,
            String recipientEmail, int failureCount, String failureCode, String deduplicationKey, Instant now) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.event = event;
        this.destination = destination;
        this.recipientEmail = recipientEmail;
        this.failureCount = failureCount;
        this.failureCode = failureCode;
        this.deduplicationKey = deduplicationKey;
        this.status = EmailNotificationStatus.PENDING;
        this.attemptCount = 0;
        this.availableAt = now;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public void markSent(Instant now) {
        status = EmailNotificationStatus.SENT;
        sentAt = now;
        leaseUntil = null;
        lastErrorCode = null;
    }

    public void retryAt(Instant nextAttemptAt, String errorCode) {
        status = EmailNotificationStatus.PENDING;
        availableAt = nextAttemptAt;
        leaseUntil = null;
        lastErrorCode = errorCode;
    }

    public void markFailed(String errorCode) {
        status = EmailNotificationStatus.FAILED;
        leaseUntil = null;
        lastErrorCode = errorCode;
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public WebhookEvent getEvent() { return event; }
    public WebhookDestination getDestination() { return destination; }
    public String getRecipientEmail() { return recipientEmail; }
    public int getFailureCount() { return failureCount; }
    public String getFailureCode() { return failureCode; }
    public String getDeduplicationKey() { return deduplicationKey; }
    public EmailNotificationStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getSentAt() { return sentAt; }
    public String getLastErrorCode() { return lastErrorCode; }
}
