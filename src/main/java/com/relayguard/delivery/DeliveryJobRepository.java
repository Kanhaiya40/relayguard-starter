package com.relayguard.delivery;

import java.util.UUID;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.repository.query.Param;

public interface DeliveryJobRepository extends JpaRepository<DeliveryJob, UUID> {
    List<DeliveryJob> findByEvent_Id(UUID eventId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from DeliveryJob j where j.event.id = :eventId")
    List<DeliveryJob> lockByEventId(@Param("eventId") UUID eventId);
    List<DeliveryJob> findByReplayAuditEvent_IdOrderByCreatedAtAsc(UUID auditEventId);
    void deleteByEvent_Id(UUID eventId);

    @Query("select case when count(j) > 0 then true else false end from DeliveryJob j "
            + "where j.event.id = :eventId and j.state = com.relayguard.delivery.DeliveryState.IN_PROGRESS")
    boolean hasActiveDelivery(@Param("eventId") UUID eventId);
}
