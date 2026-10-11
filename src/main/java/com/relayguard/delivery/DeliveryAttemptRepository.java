package com.relayguard.delivery;

import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.time.Instant;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {
    Optional<DeliveryAttempt> findFirstByJob_IdAndCompletedAtIsNullOrderByAttemptNumberDesc(UUID jobId);
    List<DeliveryAttempt> findByJob_IdOrderByAttemptNumberAsc(UUID jobId);
    void deleteByJob_Event_Id(UUID eventId);
    long countByJob_Destination_Project_IdAndJob_Destination_IdAndCompletedAtGreaterThanEqualAndOutcomeIn(
            UUID projectId, UUID destinationId, Instant since, Collection<String> outcomes);
}
