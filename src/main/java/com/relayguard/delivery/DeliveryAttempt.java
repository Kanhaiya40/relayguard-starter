package com.relayguard.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "delivery_attempts", uniqueConstraints = @UniqueConstraint(
        name = "uq_delivery_attempt_number", columnNames = {"job_id", "attempt_number"}))
public class DeliveryAttempt {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false)
    private DeliveryJob job;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(nullable = false, length = 30)
    private String outcome;

    @Column(name = "error_code", length = 50)
    private String errorCode;

    protected DeliveryAttempt() {}

    public DeliveryAttempt(DeliveryJob job, int attemptNumber, Instant startedAt) {
        this.id = UUID.randomUUID();
        this.job = job;
        this.attemptNumber = attemptNumber;
        this.startedAt = startedAt;
        this.outcome = "IN_PROGRESS";
    }

    @PrePersist
    void beforeInsert() {
        if (startedAt == null) startedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public DeliveryJob getJob() { return job; }
    public int getAttemptNumber() { return attemptNumber; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Integer getHttpStatus() { return httpStatus; }
    public Long getDurationMs() { return durationMs; }
    public String getOutcome() { return outcome; }
    public String getErrorCode() { return errorCode; }

    public void complete(Instant completedAt, long durationMs, Integer httpStatus, String outcome, String errorCode) {
        this.completedAt = completedAt;
        this.durationMs = durationMs;
        this.httpStatus = httpStatus;
        this.outcome = outcome;
        this.errorCode = errorCode;
    }
}
