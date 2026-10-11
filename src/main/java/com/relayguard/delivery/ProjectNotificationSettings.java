package com.relayguard.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_notification_settings")
public class ProjectNotificationSettings {
    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "recipient_email", nullable = false, length = 320)
    private String recipientEmail;

    @Column(name = "failure_threshold", nullable = false)
    private int failureThreshold;

    @Column(name = "window_minutes", nullable = false)
    private int windowMinutes;

    @Column(name = "cooldown_minutes", nullable = false)
    private int cooldownMinutes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ProjectNotificationSettings() {}

    public ProjectNotificationSettings(UUID projectId, boolean enabled, String recipientEmail,
            int failureThreshold, int windowMinutes, int cooldownMinutes) {
        this.projectId = projectId;
        update(enabled, recipientEmail, failureThreshold, windowMinutes, cooldownMinutes);
    }

    public void update(boolean enabled, String recipientEmail, int failureThreshold,
            int windowMinutes, int cooldownMinutes) {
        if (recipientEmail == null || recipientEmail.isBlank() || recipientEmail.length() > 320
                || failureThreshold < 1 || failureThreshold > 100
                || windowMinutes < 1 || windowMinutes > 1440
                || cooldownMinutes < 1 || cooldownMinutes > 10080) {
            throw new IllegalArgumentException("Invalid email notification settings");
        }
        this.enabled = enabled;
        this.recipientEmail = recipientEmail.trim();
        this.failureThreshold = failureThreshold;
        this.windowMinutes = windowMinutes;
        this.cooldownMinutes = cooldownMinutes;
        this.updatedAt = Instant.now();
    }

    @PrePersist
    void beforeInsert() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void beforeUpdate() { updatedAt = Instant.now(); }

    public UUID getProjectId() { return projectId; }
    public boolean isEnabled() { return enabled; }
    public String getRecipientEmail() { return recipientEmail; }
    public int getFailureThreshold() { return failureThreshold; }
    public int getWindowMinutes() { return windowMinutes; }
    public int getCooldownMinutes() { return cooldownMinutes; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
