package com.relayguard.event;

import com.relayguard.delivery.AuditEventService;
import com.relayguard.delivery.DeliveryAttemptRepository;
import com.relayguard.delivery.DeliveryJobRepository;
import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectAccessService;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventDeletionService {
    private final ProjectAccessService access;
    private final WebhookEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final DeliveryJobRepository jobs;
    private final AuditEventService audit;

    public EventDeletionService(ProjectAccessService access, WebhookEventRepository events,
            DeliveryAttemptRepository attempts, DeliveryJobRepository jobs, AuditEventService audit) {
        this.access = access;
        this.events = events;
        this.attempts = attempts;
        this.jobs = jobs;
        this.audit = audit;
    }

    @Transactional
    public void delete(UUID userId, UUID projectId, UUID eventId) {
        Project project = access.requireMember(userId, projectId);
        WebhookEvent event = events.findByIdAndProject_Id(eventId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        // Serialize deletion against workers claiming or updating this event's jobs.
        jobs.lockByEventId(eventId);
        if (jobs.hasActiveDelivery(eventId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Event cannot be deleted while delivery is in progress");
        }
        audit.record(project, userId, "EVENT_DELETED", "WEBHOOK_EVENT", eventId);
        attempts.deleteByJob_Event_Id(eventId);
        jobs.deleteByEvent_Id(eventId);
        events.delete(event);
    }
}
