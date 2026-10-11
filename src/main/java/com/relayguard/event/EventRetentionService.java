package com.relayguard.event;

import com.relayguard.delivery.DeliveryAttemptRepository;
import com.relayguard.delivery.DeliveryJobRepository;
import java.time.Instant;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EventRetentionService {
    private final WebhookEventRepository events;
    private final DeliveryAttemptRepository attempts;
    private final DeliveryJobRepository jobs;

    public EventRetentionService(WebhookEventRepository events, DeliveryAttemptRepository attempts,
            DeliveryJobRepository jobs) {
        this.events = events;
        this.attempts = attempts;
        this.jobs = jobs;
    }

    @Scheduled(fixedDelayString = "${relayguard.retention.cleanup-interval-ms:3600000}")
    @Transactional
    public int purgeExpiredBatch() {
        var expired = events.findExpiredWithoutActiveDelivery(Instant.now(),
                PageRequest.of(0, 100, Sort.by(Sort.Direction.ASC, "retentionUntil"))).getContent();
        int purged = 0;
        for (WebhookEvent event : expired) {
            jobs.lockByEventId(event.getId());
            if (jobs.hasActiveDelivery(event.getId())) continue;
            attempts.deleteByJob_Event_Id(event.getId());
            jobs.deleteByEvent_Id(event.getId());
            events.delete(event);
            purged++;
        }
        return purged;
    }
}
