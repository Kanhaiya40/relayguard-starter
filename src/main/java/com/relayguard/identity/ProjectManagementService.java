package com.relayguard.identity;

import java.util.List;
import java.util.UUID;
import com.relayguard.delivery.AuditEventService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectManagementService {
    private final ProjectRepository projects;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final ProjectCredentialService credentials;
    private final AuditEventService audit;

    public ProjectManagementService(ProjectRepository projects, WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships, ProjectCredentialService credentials, AuditEventService audit) {
        this.projects = projects;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.credentials = credentials;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<ProjectResponse> list(UUID userId, UUID workspaceId) {
        requireMembership(userId, workspaceId, false);
        return projects.findByWorkspace_IdOrderByCreatedAtDesc(workspaceId).stream()
                .map(ProjectResponse::from).toList();
    }

    @Transactional
    public ProjectResponse create(UUID userId, UUID workspaceId, CreateProjectRequest request) {
        requireMembership(userId, workspaceId, true);
        Workspace workspace = workspaces.findById(workspaceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        Project saved = projects.save(new Project(workspace, request.name().trim()));
        audit.record(saved, userId, "PROJECT_CREATED", "PROJECT", saved.getId());
        return ProjectResponse.from(saved);
    }

    @Transactional
    public ProjectCredentialResponse issueCredential(UUID userId, UUID projectId) {
        Project project = ownedProject(userId, projectId);
        IssuedProjectCredential issued = credentials.rotate(project);
        audit.record(project, userId, "PROJECT_CREDENTIAL_ISSUED", "PROJECT_CREDENTIAL", issued.credential().getId());
        return ProjectCredentialResponse.from(issued);
    }

    @Transactional
    public void revokeCredential(UUID userId, UUID projectId, UUID credentialId) {
        Project project = ownedProject(userId, projectId);
        if (!credentials.revoke(project, credentialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Project credential not found");
        }
        audit.record(project, userId, "PROJECT_CREDENTIAL_REVOKED", "PROJECT_CREDENTIAL", credentialId);
    }

    private Project ownedProject(UUID userId, UUID projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Project not found"));
        requireMembership(userId, project.getWorkspace().getId(), true);
        return project;
    }

    private void requireMembership(UUID userId, UUID workspaceId, boolean ownerRequired) {
        WorkspaceMembership membership = memberships.findByWorkspace_IdAndUser_Id(workspaceId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Workspace not found"));
        if (ownerRequired && membership.getRole() != WorkspaceRole.OWNER) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Workspace owner role is required");
        }
    }
}
