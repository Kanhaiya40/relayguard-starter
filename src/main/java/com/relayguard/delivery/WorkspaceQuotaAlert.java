package com.relayguard.delivery;

import com.relayguard.identity.Workspace;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "workspace_quota_alert_outbox", uniqueConstraints =
        @UniqueConstraint(name = "uq_workspace_quota_alert_period", columnNames = {"workspace_id", "period_start"}))
public class WorkspaceQuotaAlert {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "usage_count", nullable = false)
    private int usageCount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceQuotaAlertStatus status;

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

    protected WorkspaceQuotaAlert() {}

    public WorkspaceQuotaAlert(Workspace workspace, LocalDate periodStart, String recipientEmail, int usageCount) {
        this.id = UUID.randomUUID();
        this.workspace = workspace;
        this.periodStart = periodStart;
        this.recipientEmail = recipientEmail;
        this.usageCount = usageCount;
        this.status = WorkspaceQuotaAlertStatus.PENDING;
        this.availableAt = Instant.now();
    }

    @PrePersist
    void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }

    public boolean claim(Instant now) {
        boolean pendingAndDue = status == WorkspaceQuotaAlertStatus.PENDING && !availableAt.isAfter(now);
        boolean abandonedLease = status == WorkspaceQuotaAlertStatus.IN_PROGRESS
                && leaseUntil != null && !leaseUntil.isAfter(now);
        if (!pendingAndDue && !abandonedLease) return false;
        status = WorkspaceQuotaAlertStatus.IN_PROGRESS;
        attemptCount++;
        leaseUntil = now.plusSeconds(60);
        return true;
    }

    public void sent(Instant now) {
        status = WorkspaceQuotaAlertStatus.SENT;
        sentAt = now;
        leaseUntil = null;
    }

    public void retryOrFail(Instant now) {
        if (attemptCount >= 8) {
            status = WorkspaceQuotaAlertStatus.FAILED;
        } else {
            long delaySeconds = Math.min(3600, 30L << Math.min(attemptCount - 1, 7));
            availableAt = now.plusSeconds(delaySeconds);
            status = WorkspaceQuotaAlertStatus.PENDING;
        }
        leaseUntil = null;
    }

    public UUID getId() { return id; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getWorkspaceName() { return workspace.getName(); }
    public UUID getWorkspaceId() { return workspace.getId(); }
    public LocalDate getPeriodStart() { return periodStart; }
    public int getUsageCount() { return usageCount; }
    public int getAttemptCount() { return attemptCount; }
}
