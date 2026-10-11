package com.relayguard.identity;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectAccessService {
    private final ProjectRepository projects;
    private final WorkspaceMembershipRepository memberships;

    public ProjectAccessService(ProjectRepository projects, WorkspaceMembershipRepository memberships) {
        this.projects = projects;
        this.memberships = memberships;
    }

    public Project requireMember(UUID userId, UUID projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        memberships.findByWorkspace_IdAndUser_Id(project.getWorkspace().getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        return project;
    }

    public Project requireOwner(UUID userId, UUID projectId) {
        Project project = requireMember(userId, projectId);
        WorkspaceMembership membership = memberships.findByWorkspace_IdAndUser_Id(
                        project.getWorkspace().getId(), userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        if (membership.getRole() != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace owner role is required");
        }
        return project;
    }
}
