package com.relayguard.identity;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface WorkspaceRepository extends JpaRepository<Workspace, UUID> {
    @Query("select m.workspace from WorkspaceMembership m where m.user.id = :userId order by m.workspace.createdAt desc")
    List<Workspace> findForUser(@Param("userId") UUID userId);
}
