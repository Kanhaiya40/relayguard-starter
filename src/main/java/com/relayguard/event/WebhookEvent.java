package com.relayguard.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import com.relayguard.identity.Project;

@Entity
@Table(name = "webhook_events", uniqueConstraints = @UniqueConstraint(
        name = "uq_webhook_event_project_idempotency", columnNames = {"project_id", "idempotency_key"}))
public class WebhookEvent {
    @Id
    private UUID id;

    @Column(name = "event_type", nullable = false, length = 150)
    private String eventType;

    @Column(name = "payload_json", nullable = false, columnDefinition = "TEXT")
    private String payloadJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EventStatus status;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(name = "retention_until", nullable = false)
    private Instant retentionUntil;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", length = 64)
    private String requestFingerprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    protected WebhookEvent() {}

    public WebhookEvent(String eventType, String payloadJson) {
        this(eventType, payloadJson, null, null);
    }

    public WebhookEvent(String eventType, String payloadJson, String idempotencyKey, String requestFingerprint) {
        this(eventType, payloadJson, idempotencyKey, requestFingerprint, null);
    }

    public WebhookEvent(String eventType, String payloadJson, String idempotencyKey,
            String requestFingerprint, Project project) {
        this(eventType, payloadJson, idempotencyKey, requestFingerprint, project, 30);
    }

    public WebhookEvent(String eventType, String payloadJson, String idempotencyKey,
            String requestFingerprint, Project project, int retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("Retention must be at least one day");
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.status = EventStatus.ACCEPTED;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.project = project;
        this.retentionUntil = Instant.now().plusSeconds(retentionDays * 24L * 60 * 60);
    }

    @PrePersist
    void beforeInsert() {
        if (receivedAt == null) receivedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getEventType() { return eventType; }
    public String getPayloadJson() { return payloadJson; }
    public EventStatus getStatus() { return status; }
    public Instant getReceivedAt() { return receivedAt; }
    public Instant getRetentionUntil() { return retentionUntil; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Project getProject() { return project; }
}
