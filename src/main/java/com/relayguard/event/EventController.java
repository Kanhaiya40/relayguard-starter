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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/events")
public class EventController {
    private final EventService eventService;

    public EventController(EventService eventService) { this.eventService = eventService; }

    @PostMapping
    public ResponseEntity<EventResponse> ingest(@Valid @RequestBody IngestEventRequest request) {
        EventResponse response = eventService.ingest(request);
        return ResponseEntity.accepted().location(URI.create("/api/v1/events/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public EventResponse get(@PathVariable UUID id) { return eventService.get(id); }
}
