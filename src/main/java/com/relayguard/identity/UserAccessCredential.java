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
@Table(name = "user_access_credentials")
public class UserAccessCredential {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @Column(name = "key_prefix", nullable = false, unique = true, length = 24)
    private String keyPrefix;

    @Column(name = "credential_hash", nullable = false, unique = true, length = 64)
    private String credentialHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserAccessCredential() {}

    public UserAccessCredential(UserAccount user, String keyPrefix, String credentialHash) {
        this.id = UUID.randomUUID();
        this.user = user;
        this.keyPrefix = keyPrefix;
        this.credentialHash = credentialHash;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
        if (expiresAt == null) expiresAt = createdAt.plusSeconds(30L * 24 * 60 * 60);
    }

    public UUID getId() { return id; }
    public UserAccount getUser() { return user; }
    public String getKeyPrefix() { return keyPrefix; }
    public String getCredentialHash() { return credentialHash; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getRevokedAt() { return revokedAt; }

    public boolean revoke() {
        if (revokedAt != null) return false;
        revokedAt = Instant.now();
        return true;
    }
}
