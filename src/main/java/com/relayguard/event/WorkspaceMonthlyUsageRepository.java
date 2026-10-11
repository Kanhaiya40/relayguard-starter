package com.relayguard.event;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceMonthlyUsageRepository extends JpaRepository<WorkspaceMonthlyUsage, UUID> {
    Optional<WorkspaceMonthlyUsage> findByWorkspace_IdAndPeriodStart(UUID workspaceId, LocalDate periodStart);
}
