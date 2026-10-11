package com.relayguard.event;

import com.relayguard.identity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_rate_limit_counters")
public class ProjectRateLimitCounter {
    @Id
    @Column(name = "project_id")
    private UUID projectId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Column(name = "request_count", nullable = false)
    private int requestCount;

    protected ProjectRateLimitCounter() {}

    public ProjectRateLimitCounter(Project project, Instant windowStartedAt) {
        this.project = project;
        this.windowStartedAt = windowStartedAt;
        this.requestCount = 0;
    }

    public void register(Instant window) {
        if (!windowStartedAt.equals(window)) {
            windowStartedAt = window;
            requestCount = 0;
        }
        requestCount++;
    }

    public int getRequestCount() { return requestCount; }
}
