package com.relayguard.delivery;

import com.relayguard.event.WebhookEvent;
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
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_jobs")
public class DeliveryJob {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private WebhookEvent event;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private WebhookDestination destination;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replay_audit_event_id")
    private AuditEvent replayAuditEvent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryState state;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Column(name = "last_error_code", length = 50)
    private String lastErrorCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DeliveryJob() {}

    public DeliveryJob(WebhookEvent event, WebhookDestination destination) {
        this(event, destination, null);
    }

    public DeliveryJob(WebhookEvent event, WebhookDestination destination, AuditEvent replayAuditEvent) {
        this.id = UUID.randomUUID();
        this.event = event;
        this.destination = destination;
        this.replayAuditEvent = replayAuditEvent;
        this.state = DeliveryState.PENDING;
        this.attemptCount = 0;
        this.nextAttemptAt = Instant.now();
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    public UUID getId() { return id; }
    public WebhookEvent getEvent() { return event; }
    public WebhookDestination getDestination() { return destination; }
    public AuditEvent getReplayAuditEvent() { return replayAuditEvent; }
    public DeliveryState getState() { return state; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Integer getLastHttpStatus() { return lastHttpStatus; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void markInProgress(Instant leaseUntil) {
        state = DeliveryState.IN_PROGRESS;
        attemptCount++;
        this.leaseUntil = leaseUntil;
        updatedAt = Instant.now();
    }

    public void markDelivered(Integer statusCode) {
        state = DeliveryState.DELIVERED;
        lastHttpStatus = statusCode;
        lastErrorCode = null;
        leaseUntil = null;
        updatedAt = Instant.now();
    }

    public void markExhausted(Integer statusCode, String errorCode) {
        state = DeliveryState.EXHAUSTED;
        lastHttpStatus = statusCode;
        lastErrorCode = errorCode;
        leaseUntil = null;
        updatedAt = Instant.now();
    }

    public void scheduleRetry(Integer statusCode, String errorCode, Instant nextAttemptAt) {
        state = DeliveryState.PENDING;
        lastHttpStatus = statusCode;
        lastErrorCode = errorCode;
        this.nextAttemptAt = nextAttemptAt;
        leaseUntil = null;
        updatedAt = Instant.now();
    }
}
