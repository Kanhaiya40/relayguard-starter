package com.relayguard.delivery;

import com.relayguard.identity.Project;
import com.relayguard.identity.UserAccount;
import com.relayguard.identity.Workspace;
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
@Table(name = "audit_events", uniqueConstraints = @UniqueConstraint(name = "uq_audit_project_action_request",
        columnNames = {"project_id", "action", "request_key"}))
public class AuditEvent {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "project_id", nullable = false)
    private Project project;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "actor_user_id", nullable = false)
    private UserAccount actor;
    @Column(nullable = false, length = 60) private String action;
    @Column(name = "target_type", nullable = false, length = 60) private String targetType;
    @Column(name = "target_id", nullable = false) private UUID targetId;
    @Column(name = "request_key", nullable = false, length = 128) private String requestKey;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected AuditEvent() {}

    public AuditEvent(Workspace workspace, Project project, UserAccount actor, String action,
            String targetType, UUID targetId, String requestKey) {
        this.id = UUID.randomUUID(); this.workspace = workspace; this.project = project; this.actor = actor;
        this.action = action; this.targetType = targetType; this.targetId = targetId; this.requestKey = requestKey;
    }

    @PrePersist void beforeInsert() { if (createdAt == null) createdAt = Instant.now(); }
    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public UUID getTargetId() { return targetId; }
    public String getAction() { return action; }
    public String getTargetType() { return targetType; }
    public Instant getCreatedAt() { return createdAt; }
    public String getRequestKey() { return requestKey; }
}
