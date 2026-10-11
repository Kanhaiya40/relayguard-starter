package com.relayguard.delivery;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailNotificationOutboxRepository extends JpaRepository<EmailNotificationOutboxEntry, UUID> {
    List<EmailNotificationOutboxEntry> findByEvent_Id(UUID eventId);
    void deleteByEvent_Id(UUID eventId);
}
