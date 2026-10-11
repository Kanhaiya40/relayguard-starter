package com.relayguard.identity;

import java.util.UUID;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkspaceMembershipRepository extends JpaRepository<WorkspaceMembership, UUID> {
    Optional<WorkspaceMembership> findByWorkspace_IdAndUser_Id(UUID workspaceId, UUID userId);
    List<WorkspaceMembership> findByWorkspace_IdAndRole(UUID workspaceId, WorkspaceRole role);
}
