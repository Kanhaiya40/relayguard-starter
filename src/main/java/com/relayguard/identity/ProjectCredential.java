package com.relayguard.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_credentials")
public class ProjectCredential {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "key_prefix", nullable = false, unique = true, length = 24)
    private String keyPrefix;

    @Column(name = "credential_hash", nullable = false, unique = true, length = 64)
    private String credentialHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ProjectCredential() {}

    ProjectCredential(Project project, String keyPrefix, String credentialHash) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.keyPrefix = keyPrefix;
        this.credentialHash = credentialHash;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getCredentialHash() { return credentialHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean revoke() {
        if (revokedAt != null) return false;
        revokedAt = Instant.now();
        return true;
    }
}
