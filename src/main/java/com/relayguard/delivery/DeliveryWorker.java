package com.relayguard.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "relayguard.delivery", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DeliveryWorker {
    private final DeliveryQueueService queue;
    private final DeliveryTransport transport;

    public DeliveryWorker(DeliveryQueueService queue, DeliveryTransport transport) {
        this.queue = queue;
        this.transport = transport;
    }

    @Scheduled(fixedDelayString = "${relayguard.delivery.poll-interval-ms:1000}")
    public void processNext() {
        queue.claimNext().ifPresent(claim -> queue.complete(claim, transport.send(claim)));
    }
}
