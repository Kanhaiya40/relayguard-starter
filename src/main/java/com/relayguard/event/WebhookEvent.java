package com.relayguard.event;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
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

    protected WebhookEvent() {}

    public WebhookEvent(String eventType, String payloadJson) {
        this.id = UUID.randomUUID();
        this.eventType = eventType;
        this.payloadJson = payloadJson;
        this.status = EventStatus.ACCEPTED;
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
}
