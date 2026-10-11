package com.relayguard.event;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID>,
        org.springframework.data.jpa.repository.JpaSpecificationExecutor<WebhookEvent> {
    Optional<WebhookEvent> findByProject_IdAndIdempotencyKey(java.util.UUID projectId, String idempotencyKey);
    Optional<WebhookEvent> findByIdAndProject_Id(UUID id, java.util.UUID projectId);
    @Query("select e from WebhookEvent e where e.retentionUntil < :cutoff and not exists "
            + "(select j.id from DeliveryJob j where j.event = e and j.state = "
            + "com.relayguard.delivery.DeliveryState.IN_PROGRESS) order by e.retentionUntil asc")
    Page<WebhookEvent> findExpiredWithoutActiveDelivery(@Param("cutoff") Instant cutoff, Pageable pageable);

}
