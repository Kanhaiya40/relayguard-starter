package com.relayguard.event;

import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectRateLimitService {
    private final ProjectRepository projects;
    private final ProjectRateLimitCounterRepository counters;
    private final int requestsPerMinute;

    public ProjectRateLimitService(ProjectRepository projects, ProjectRateLimitCounterRepository counters,
            @Value("${relayguard.rate-limit.events-per-minute:600}") int requestsPerMinute) {
        if (requestsPerMinute < 1) throw new IllegalArgumentException("Rate limit must be positive");
        this.projects = projects;
        this.counters = counters;
        this.requestsPerMinute = requestsPerMinute;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Decision registerRequest(UUID projectId) {
        Project project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Project credential is invalid"));
        Instant now = Instant.now();
        Instant minute = Instant.ofEpochSecond(now.getEpochSecond() / 60 * 60);
        ProjectRateLimitCounter counter = counters.findById(projectId)
                .orElseGet(() -> new ProjectRateLimitCounter(project, minute));
        counter.register(minute);
        counters.saveAndFlush(counter);
        long retryAfter = 60 - now.getEpochSecond() % 60;
        return new Decision(counter.getRequestCount() <= requestsPerMinute, retryAfter);
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {}
}
