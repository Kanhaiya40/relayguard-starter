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
    private final DeliveryRetryPolicy defaults;

    public DestinationService(WebhookDestinationRepository destinations, ProjectAccessService access,
            DestinationUrlValidator validator, AuditEventService audit, DeliveryRetryPolicy defaults) {
        this.destinations = destinations;
        this.access = access;
        this.validator = validator;
        this.audit = audit;
        this.defaults = defaults;
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
        int maxAttempts = request.retryMaxAttempts() == null ? defaults.maxAttempts() : request.retryMaxAttempts();
        long baseDelay = request.retryBaseDelayMs() == null ? defaults.baseDelayMs() : request.retryBaseDelayMs();
        long maxDelay = request.retryMaxDelayMs() == null ? defaults.maxDelayMs() : request.retryMaxDelayMs();
        if (maxDelay < baseDelay) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum retry delay must be at least the base delay");
        }
        WebhookDestination saved = destinations.save(new WebhookDestination(
                project, valid.toASCIIString(), maxAttempts, baseDelay, maxDelay));
        audit.record(project, userId, "DESTINATION_CREATED", "WEBHOOK_DESTINATION", saved.getId());
        return DestinationResponse.from(saved);
    }

    @Transactional
    public DestinationResponse updateRetryPolicy(UUID userId, UUID projectId, UUID destinationId,
            UpdateDestinationRetryPolicyRequest request) {
        Project project = access.requireOwner(userId, projectId);
        WebhookDestination destination = destinations.findByIdAndProject_Id(destinationId, project.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found"));
        if (request.retryMaxDelayMs() < request.retryBaseDelayMs()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Maximum retry delay must be at least the base delay");
        }
        destination.setRetryPolicy(request.retryMaxAttempts(), request.retryBaseDelayMs(), request.retryMaxDelayMs());
        audit.record(project, userId, "DESTINATION_RETRY_POLICY_UPDATED", "WEBHOOK_DESTINATION", destinationId);
        return DestinationResponse.from(destination);
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
