package com.relayguard.delivery;

import com.relayguard.event.WebhookEvent;
import com.relayguard.event.WebhookEventRepository;
import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectAccessService;
import com.relayguard.identity.ProjectRepository;
import com.relayguard.identity.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReplayService {
    private static final String REPLAY_ACTION = "EVENT_REPLAY_REQUESTED";

    private final ProjectAccessService access;
    private final ProjectRepository projects;
    private final UserAccountRepository users;
    private final WebhookEventRepository events;
    private final WebhookDestinationRepository destinations;
    private final DeliveryJobRepository jobs;
    private final AuditEventRepository auditEvents;

    public ReplayService(ProjectAccessService access, ProjectRepository projects, UserAccountRepository users,
            WebhookEventRepository events, WebhookDestinationRepository destinations,
            DeliveryJobRepository jobs, AuditEventRepository auditEvents) {
        this.access = access;
        this.projects = projects;
        this.users = users;
        this.events = events;
        this.destinations = destinations;
        this.jobs = jobs;
        this.auditEvents = auditEvents;
    }

    @Transactional
    public ReplayResponse replay(UUID userId, UUID projectId, UUID eventId, String requestKey) {
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9._:-]{1,128}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A valid Idempotency-Key is required");
        }
        access.requireMember(userId, projectId);
        Project project = projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        WebhookEvent event = events.findByIdAndProject_Id(eventId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));

        var existing = auditEvents.findByProject_IdAndActionAndRequestKey(projectId, REPLAY_ACTION, requestKey);
        if (existing.isPresent()) {
            AuditEvent audit = existing.get();
            if (!audit.getTargetId().equals(eventId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency-Key was already used for another replay");
            }
            return response(audit, true);
        }

        List<WebhookDestination> enabled = destinations
                .findByProject_IdAndEnabledTrueOrderByCreatedAtDesc(projectId);
        if (enabled.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "No enabled destinations are configured");
        }

        AuditEvent audit = auditEvents.saveAndFlush(new AuditEvent(project.getWorkspace(), project,
                users.findById(userId).orElseThrow(), REPLAY_ACTION, "WEBHOOK_EVENT", eventId, requestKey));
        jobs.saveAllAndFlush(enabled.stream().map(destination -> new DeliveryJob(event, destination, audit)).toList());
        return response(audit, false);
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> audit(UUID userId, UUID projectId, UUID eventId) {
        access.requireMember(userId, projectId);
        if (events.findByIdAndProject_Id(eventId, projectId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found");
        }
        return auditEvents.findByProject_IdAndTargetIdOrderByCreatedAtDesc(projectId, eventId)
                .stream().map(AuditEventResponse::from).toList();
    }

    private ReplayResponse response(AuditEvent audit, boolean replayed) {
        List<UUID> jobIds = jobs.findByReplayAuditEvent_IdOrderByCreatedAtAsc(audit.getId()).stream()
                .map(DeliveryJob::getId).toList();
        return new ReplayResponse(audit.getId(), audit.getTargetId(), jobIds, replayed);
    }
}
