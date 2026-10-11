package com.relayguard.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EventService {
    private final WebhookEventRepository repository;
    private final ObjectMapper objectMapper;

    public EventService(WebhookEventRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public EventResponse ingest(IngestEventRequest request) {
        try {
            String payload = objectMapper.writeValueAsString(request.payload());
            return EventResponse.from(repository.save(new WebhookEvent(request.eventType().trim(), payload)));
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payload must be valid JSON", exception);
        }
    }

    public EventResponse get(UUID id) {
        return repository.findById(id)
                .map(EventResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
    }
}
