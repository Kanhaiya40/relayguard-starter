package com.relayguard.delivery;

import com.relayguard.event.WebhookEventRepository;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliveryHistoryService {
    private final WebhookEventRepository events;
    private final DeliveryJobRepository jobs;
    private final DeliveryAttemptRepository attempts;

    public DeliveryHistoryService(WebhookEventRepository events, DeliveryJobRepository jobs,
            DeliveryAttemptRepository attempts) {
        this.events = events;
        this.jobs = jobs;
        this.attempts = attempts;
    }

    @Transactional(readOnly = true)
    public List<DeliveryHistoryResponse> forEvent(UUID projectId, UUID eventId) {
        events.findByIdAndProject_Id(eventId, projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Event not found"));
        return jobs.findByEvent_Id(eventId).stream().map(job -> {
            String host = URI.create(job.getDestination().getTargetUrl()).getHost();
            List<DeliveryAttemptResponse> history = attempts.findByJob_IdOrderByAttemptNumberAsc(job.getId())
                    .stream().map(DeliveryAttemptResponse::from).toList();
            return new DeliveryHistoryResponse(job.getId(), job.getDestination().getId(), host,
                    job.getState(), job.getAttemptCount(), job.getLastHttpStatus(), job.getLastErrorCode(), history);
        }).toList();
    }
}
