package com.relayguard.delivery;

import com.relayguard.identity.IdentityRequestAttributes;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/destinations")
public class DestinationController {
    private final DestinationService destinations;

    public DestinationController(DestinationService destinations) { this.destinations = destinations; }

    @GetMapping
    public List<DestinationResponse> list(@PathVariable UUID projectId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return destinations.list(userId, projectId);
    }

    @PostMapping
    public ResponseEntity<DestinationResponse> create(@PathVariable UUID projectId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId,
            @Valid @RequestBody CreateDestinationRequest request) {
        DestinationResponse destination = destinations.create(userId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(URI.create("/api/v1/projects/" + projectId + "/destinations/" + destination.id()))
                .body(destination);
    }

    @DeleteMapping("/{destinationId}")
    public ResponseEntity<Void> disable(@PathVariable UUID projectId, @PathVariable UUID destinationId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        destinations.disable(userId, projectId, destinationId);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{destinationId}/retry-policy")
    public DestinationResponse updateRetryPolicy(@PathVariable UUID projectId, @PathVariable UUID destinationId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId,
            @Valid @RequestBody UpdateDestinationRetryPolicyRequest request) {
        return destinations.updateRetryPolicy(userId, projectId, destinationId, request);
    }
}
