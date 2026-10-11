package com.relayguard.delivery;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Pageable;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    Optional<AuditEvent> findByProject_IdAndActionAndRequestKey(UUID projectId, String action, String requestKey);
    List<AuditEvent> findByProject_IdAndTargetIdOrderByCreatedAtDesc(UUID projectId, UUID targetId);
    List<AuditEvent> findByProject_IdOrderByCreatedAtDesc(UUID projectId, Pageable pageable);
}
