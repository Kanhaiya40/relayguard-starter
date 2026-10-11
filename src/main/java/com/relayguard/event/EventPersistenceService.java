package com.relayguard.event;

import com.relayguard.delivery.DeliveryOutbox;
import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectRepository;
import java.util.UUID;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventPersistenceService {
    private final WebhookEventRepository events;
    private final ProjectRepository projects;
    private final DeliveryOutbox outbox;
    private final Counter acceptedEvents;
    private final int retentionDays;

    public EventPersistenceService(WebhookEventRepository events, ProjectRepository projects, DeliveryOutbox outbox,
            MeterRegistry meters, @Value("${relayguard.retention.days:30}") int retentionDays) {
        if (retentionDays < 1) throw new IllegalArgumentException("Retention must be at least one day");
        this.events = events;
        this.projects = projects;
        this.outbox = outbox;
        this.acceptedEvents = Counter.builder("relayguard.events.accepted").register(meters);
        this.retentionDays = retentionDays;
    }

    @Transactional
    public EventResponse accept(String eventType, String payload, String idempotencyKey,
            String fingerprint, UUID projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Project credential is invalid"));
        WebhookEvent event = events.saveAndFlush(
                new WebhookEvent(eventType, payload, idempotencyKey, fingerprint, project, retentionDays));
        outbox.enqueueFor(event);
        acceptedEvents.increment();
        return EventResponse.from(event);
    }
}
