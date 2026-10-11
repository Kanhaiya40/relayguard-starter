package com.relayguard.delivery;

import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectAccessService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DestinationService {
    private final WebhookDestinationRepository destinations;
    private final ProjectAccessService access;
    private final DestinationUrlValidator validator;
    private final AuditEventService audit;

    public DestinationService(WebhookDestinationRepository destinations, ProjectAccessService access,
            DestinationUrlValidator validator, AuditEventService audit) {
        this.destinations = destinations;
        this.access = access;
        this.validator = validator;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<DestinationResponse> list(UUID userId, UUID projectId) {
        access.requireMember(userId, projectId);
        return destinations.findByProject_IdAndEnabledTrueOrderByCreatedAtDesc(projectId).stream()
                .map(DestinationResponse::from).toList();
    }

    @Transactional
    public DestinationResponse create(UUID userId, UUID projectId, CreateDestinationRequest request) {
        Project project = access.requireOwner(userId, projectId);
        URI valid = validator.validate(request.url());
        WebhookDestination saved = destinations.save(new WebhookDestination(project, valid.toASCIIString()));
        audit.record(project, userId, "DESTINATION_CREATED", "WEBHOOK_DESTINATION", saved.getId());
        return DestinationResponse.from(saved);
    }

    @Transactional
    public void disable(UUID userId, UUID projectId, UUID destinationId) {
        Project project = access.requireOwner(userId, projectId);
        WebhookDestination destination = destinations.findByIdAndProject_Id(destinationId, project.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found"));
        destination.disable();
        audit.record(project, userId, "DESTINATION_DISABLED", "WEBHOOK_DESTINATION", destinationId);
    }
}
