package com.relayguard.delivery;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkspaceQuotaAlertRepository extends JpaRepository<WorkspaceQuotaAlert, UUID> {
    boolean existsByWorkspace_IdAndPeriodStart(UUID workspaceId, LocalDate periodStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from WorkspaceQuotaAlert a where (a.status = :status and a.availableAt <= :now) "
            + "or (a.status = :inProgress and a.leaseUntil <= :now) order by a.createdAt")
    java.util.List<WorkspaceQuotaAlert> findPendingForUpdate(
            @Param("status") WorkspaceQuotaAlertStatus status,
            @Param("inProgress") WorkspaceQuotaAlertStatus inProgress,
            @Param("now") java.time.Instant now, Pageable pageable);
}
