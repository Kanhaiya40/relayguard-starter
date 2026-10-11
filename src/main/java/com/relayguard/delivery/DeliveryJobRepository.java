package com.relayguard.delivery;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, UUID> {
    List<DeliveryJob> findByEvent_Id(UUID eventId);
    List<DeliveryJob> findByReplayAuditEvent_IdOrderByCreatedAtAsc(UUID auditEventId);
}
