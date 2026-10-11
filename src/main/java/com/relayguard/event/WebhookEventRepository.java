package com.relayguard.event;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<WebhookEvent> {
    Optional<WebhookEvent> findByProject_IdAndIdempotencyKey(java.util.UUID projectId, String idempotencyKey);
    Optional<WebhookEvent> findByIdAndProject_Id(UUID id, java.util.UUID projectId);

}
