package com.relayguard.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import java.util.Optional;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    @Query("select m.workspace from WorkspaceMembership m where m.user.id = :userId order by m.workspace.createdAt desc")
    List<Workspace> findForUser(@Param("userId") UUID userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Workspace w where w.id = :id")
    Optional<Workspace> findByIdForUpdate(@Param("id") UUID id);
}
