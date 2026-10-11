package com.relayguard.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces")
public class Workspace {
    @Id
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Workspace() {}

    public Workspace(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
}
