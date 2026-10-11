package com.relayguard.identity;

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
@Table(name = "projects", uniqueConstraints =
        @UniqueConstraint(name = "uq_project_workspace_name", columnNames = {"workspace_id", "name"}))
public class Project {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workspace_id", nullable = false)
    private Workspace workspace;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Project() {}

    public Project(Workspace workspace, String name) {
        this.id = UUID.randomUUID();
        this.workspace = workspace;
        this.name = name;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Workspace getWorkspace() { return workspace; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
