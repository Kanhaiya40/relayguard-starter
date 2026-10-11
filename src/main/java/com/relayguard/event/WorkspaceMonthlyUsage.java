package com.relayguard.event;

import com.relayguard.identity.Workspace;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "workspace_monthly_usage", uniqueConstraints =
        @UniqueConstraint(name = "uq_workspace_monthly_usage_period", columnNames = {"workspace_id", "period_start"}))
public class WorkspaceMonthlyUsage {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(name = "period_start", nullable = false)
    private LocalDate periodStart;

    @Column(name = "accepted_events", nullable = false)
    private int acceptedEvents;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WorkspaceMonthlyUsage() {}

    public WorkspaceMonthlyUsage(Workspace workspace, LocalDate periodStart) {
        this.id = UUID.randomUUID();
        this.workspace = workspace;
        this.periodStart = periodStart;
    }

    public int increment() {
        acceptedEvents++;
        return acceptedEvents;
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public UUID getWorkspaceId() { return workspace.getId(); }
    public LocalDate getPeriodStart() { return periodStart; }
    public int getAcceptedEvents() { return acceptedEvents; }
}
