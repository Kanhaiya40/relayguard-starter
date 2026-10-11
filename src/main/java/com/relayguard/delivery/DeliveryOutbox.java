package com.relayguard.delivery;

import com.relayguard.event.WebhookEvent;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryOutbox {
    private final WebhookDestinationRepository destinations;
    private final DeliveryJobRepository jobs;

    public DeliveryOutbox(WebhookDestinationRepository destinations, DeliveryJobRepository jobs) {
        this.destinations = destinations;
        this.jobs = jobs;
    }

    @Transactional
    public void enqueueFor(WebhookEvent event) {
        List<DeliveryJob> pending = destinations
                .findByProject_IdAndEnabledTrueOrderByCreatedAtDesc(event.getProject().getId()).stream()
                .map(destination -> new DeliveryJob(event, destination))
                .toList();
        jobs.saveAll(pending);
    }
}
