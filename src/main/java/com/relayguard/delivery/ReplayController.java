package com.relayguard.delivery;

import com.relayguard.identity.IdentityRequestAttributes;
import com.relayguard.event.EventDeletionService;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/events/{eventId}")
public class ReplayController {
    private final ReplayService replays;
    private final EventDeletionService eventDeletion;

    public ReplayController(ReplayService replays, EventDeletionService eventDeletion) {
        this.replays = replays;
        this.eventDeletion = eventDeletion;
    }

    @PostMapping("/replays")
    public ResponseEntity<ReplayResponse> replay(@PathVariable UUID projectId, @PathVariable UUID eventId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId,
            @RequestHeader(name = "Idempotency-Key", required = false) String requestKey) {
        ReplayResponse response = replays.replay(userId, projectId, eventId, requestKey);
        return ResponseEntity.accepted().location(URI.create("/api/v1/events/" + eventId + "/deliveries"))
                .body(response);
    }

    @GetMapping("/audit")
    public List<AuditEventResponse> audit(@PathVariable UUID projectId, @PathVariable UUID eventId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return replays.audit(userId, projectId, eventId);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteEvent(@PathVariable UUID projectId, @PathVariable UUID eventId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        eventDeletion.delete(userId, projectId, eventId);
        return ResponseEntity.noContent().build();
    }
}
