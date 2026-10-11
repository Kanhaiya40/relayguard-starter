package com.relayguard.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.relayguard.delivery.DeliveryJob;
import com.relayguard.delivery.DeliveryState;
import com.relayguard.delivery.WebhookDestinationRepository;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {
    private final WebhookEventRepository repository;
    private final ObjectMapper objectMapper;
    private final EventPersistenceService persistence;
    private final WebhookDestinationRepository destinations;

    public EventService(WebhookEventRepository repository, ObjectMapper objectMapper,
            EventPersistenceService persistence, WebhookDestinationRepository destinations) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.persistence = persistence;
        this.destinations = destinations;
    }

    public EventResponse ingest(IngestEventRequest request, String idempotencyKey, UUID projectId) {
        if (idempotencyKey != null && (idempotencyKey.isBlank() || idempotencyKey.length() > 255)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Idempotency-Key must contain 1 to 255 characters");
        }

        try {
            String payload = objectMapper.writeValueAsString(request.payload());
            String eventType = request.eventType().trim();
            String fingerprint = idempotencyKey == null ? null : fingerprint(eventType, request.payload());
            if (idempotencyKey != null) {
                var existing = repository.findByProject_IdAndIdempotencyKey(projectId, idempotencyKey);
                if (existing.isPresent()) return existingResponse(existing.get(), fingerprint);
            }
            try {
                return persistence.accept(eventType, payload, idempotencyKey, fingerprint, projectId);
            } catch (DataIntegrityViolationException exception) {
                if (idempotencyKey == null) throw exception;
                return repository.findByProject_IdAndIdempotencyKey(projectId, idempotencyKey)
                        .map(event -> existingResponse(event, fingerprint))
                        .orElseThrow(() -> exception);
            }
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload must be valid JSON", exception);
        }
    }

    private EventResponse existingResponse(WebhookEvent existing, String fingerprint) {
        if (!MessageDigest.isEqual(existing.getRequestFingerprint().getBytes(StandardCharsets.US_ASCII),
                fingerprint.getBytes(StandardCharsets.US_ASCII))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency-Key was already used for a different request");
        }
        return EventResponse.from(existing);
    }

    private String fingerprint(String eventType, JsonNode payload) throws JsonProcessingException {
        ObjectNode canonical = objectMapper.createObjectNode();
        canonical.put("eventType", eventType);
        canonical.set("payload", canonicalize(payload));
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(canonical));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private JsonNode canonicalize(JsonNode node) {
        if (node.isObject()) {
            ObjectNode sorted = objectMapper.createObjectNode();
            Map<String, JsonNode> fields = new TreeMap<>();
            node.properties().forEach(entry -> fields.put(entry.getKey(), entry.getValue()));
            fields.forEach((key, value) -> sorted.set(key, canonicalize(value)));
            return sorted;
        }
        if (node.isArray()) {
            ArrayNode ordered = objectMapper.createArrayNode();
            node.forEach(value -> ordered.add(canonicalize(value)));
            return ordered;
        }
        return node;
    }

    public EventResponse get(UUID id, UUID projectId) {
        return repository.findByIdAndProject_Id(id, projectId)
                .map(EventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }

    public EventDetailResponse detail(UUID id, UUID projectId) {
        WebhookEvent event = repository.findByIdAndProject_Id(id, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        try {
            return new EventDetailResponse(event.getId(), event.getEventType(), event.getStatus().name(),
                    event.getReceivedAt(), objectMapper.readTree(event.getPayloadJson()));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Persisted event payload is not valid JSON", exception);
        }
    }

    public EventListResponse list(UUID projectId, String eventType, Instant fromTime, Instant toTime,
            UUID destinationId, DeliveryState deliveryState, int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (fromTime != null && toTime != null && !fromTime.isBefore(toTime))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid event list filters or pagination");
        }
        if (destinationId != null && destinations.findByIdAndProject_Id(destinationId, projectId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Destination not found");
        }
        String typeFilter = eventType == null || eventType.isBlank() ? null : eventType.trim().toLowerCase(java.util.Locale.ROOT);
        Specification<WebhookEvent> specification = (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("project").get("id"), projectId));
            if (typeFilter != null) predicates.add(builder.like(builder.lower(root.get("eventType")), "%" + typeFilter + "%"));
            if (fromTime != null) predicates.add(builder.greaterThanOrEqualTo(root.get("receivedAt"), fromTime));
            if (toTime != null) predicates.add(builder.lessThan(root.get("receivedAt"), toTime));
            if (destinationId != null || deliveryState != null) {
                Subquery<UUID> matchingEvents = query.subquery(UUID.class);
                var job = matchingEvents.from(DeliveryJob.class);
                matchingEvents.select(job.get("event").get("id"));
                List<Predicate> jobPredicates = new ArrayList<>();
                jobPredicates.add(builder.equal(job.get("event").get("id"), root.get("id")));
                if (destinationId != null) jobPredicates.add(builder.equal(job.get("destination").get("id"), destinationId));
                if (deliveryState != null) jobPredicates.add(builder.equal(job.get("state"), deliveryState));
                matchingEvents.where(builder.and(jobPredicates.toArray(Predicate[]::new)));
                predicates.add(builder.exists(matchingEvents));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
        Page<WebhookEvent> result = repository.findAll(specification,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt")));
        return new EventListResponse(result.getContent().stream().map(EventSummaryResponse::from).toList(),
                page, size, result.hasNext());
    }
}
