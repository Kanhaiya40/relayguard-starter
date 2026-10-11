package com.relayguard.event;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestAttribute;
import com.relayguard.identity.IdentityRequestAttributes;
import com.relayguard.delivery.DeliveryHistoryService;
import com.relayguard.delivery.DeliveryHistoryResponse;
import com.relayguard.config.ApiErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import java.time.Instant;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;
    private final DeliveryHistoryService deliveryHistory;
    private final ProjectRateLimitService rateLimits;

    public EventController(EventService eventService, DeliveryHistoryService deliveryHistory,
            ProjectRateLimitService rateLimits) {
        this.eventService = eventService;
        this.deliveryHistory = deliveryHistory;
        this.rateLimits = rateLimits;
    }

    @PostMapping
    public ResponseEntity<?> ingest(
            @Valid @RequestBody IngestEventRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestAttribute(IdentityRequestAttributes.PROJECT_ID) UUID projectId) {
        ProjectRateLimitService.Decision decision = rateLimits.registerRequest(projectId);
        if (!decision.allowed()) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", Long.toString(decision.retryAfterSeconds()))
                    .body(ApiErrorResponse.of("RATE_LIMIT_EXCEEDED", "Project event ingestion limit exceeded", 429));
        }
        EventResponse response = eventService.ingest(request, idempotencyKey, projectId);
        return ResponseEntity.accepted().location(URI.create("/api/v1/events/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public EventDetailResponse get(@PathVariable UUID id,
            @RequestAttribute(IdentityRequestAttributes.PROJECT_ID) UUID projectId) {
        return eventService.detail(id, projectId);
    }

    @GetMapping
    public EventListResponse list(@RequestAttribute(IdentityRequestAttributes.PROJECT_ID) UUID projectId,
            @RequestParam(required = false) String eventType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return eventService.list(projectId, eventType, from, to, page, size);
    }

    @GetMapping("/{id}/deliveries")
    public java.util.List<DeliveryHistoryResponse> deliveries(@PathVariable UUID id,
            @RequestAttribute(IdentityRequestAttributes.PROJECT_ID) UUID projectId) {
        return deliveryHistory.forEvent(projectId, id);
    }
}
