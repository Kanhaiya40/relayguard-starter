package com.relayguard.delivery;

import com.relayguard.identity.Project;
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
@Table(name = "webhook_destinations", uniqueConstraints = @UniqueConstraint(
        name = "uq_destination_project_url", columnNames = {"project_id", "target_url"}))
public class WebhookDestination {
    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "target_url", nullable = false, length = 2048)
    private String targetUrl;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebhookDestination() {}

    public WebhookDestination(Project project, String targetUrl) {
        this.id = UUID.randomUUID();
        this.project = project;
        this.targetUrl = targetUrl;
        this.enabled = true;
    }

    @PrePersist
    void beforeInsert() {
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Project getProject() { return project; }
    public String getTargetUrl() { return targetUrl; }
    public boolean isEnabled() { return enabled; }
    public Instant getCreatedAt() { return createdAt; }

    public void disable() { enabled = false; }
}
