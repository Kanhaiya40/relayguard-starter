package com.relayguard.delivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookDestinationRepository extends JpaRepository<WebhookDestination, UUID> {
    List<WebhookDestination> findByProject_IdAndEnabledTrueOrderByCreatedAtDesc(UUID projectId);
    Optional<WebhookDestination> findByIdAndProject_Id(UUID id, UUID projectId);
}
