package com.relayguard.identity;

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

@Entity
@Table(name = "workspace_memberships", uniqueConstraints =
        @UniqueConstraint(name = "uq_workspace_membership_user", columnNames = {"workspace_id", "user_id"}))
public class WorkspaceMembership {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WorkspaceRole role;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WorkspaceMembership() {}

    public WorkspaceMembership(Workspace workspace, UserAccount user, WorkspaceRole role) {
        this.id = UUID.randomUUID();
        this.workspace = workspace;
        this.user = user;
        this.role = role;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public UserAccount getUser() { return user; }
    public WorkspaceRole getRole() { return role; }
    public Instant getCreatedAt() { return createdAt; }
}
