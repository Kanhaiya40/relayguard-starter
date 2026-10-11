package com.relayguard.identity;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ProjectController {
    private final ProjectManagementService projects;

    public ProjectController(ProjectManagementService projects) { this.projects = projects; }

    @GetMapping("/workspaces/{workspaceId}/projects")
    public List<ProjectResponse> list(@PathVariable UUID workspaceId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return projects.list(userId, workspaceId);
    }

    @PostMapping("/workspaces/{workspaceId}/projects")
    public ResponseEntity<ProjectResponse> create(@PathVariable UUID workspaceId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId,
            @Valid @RequestBody CreateProjectRequest request) {
        ProjectResponse project = projects.create(userId, workspaceId, request);
        return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
    }

    @PostMapping("/projects/{projectId}/credentials")
    public ResponseEntity<ProjectCredentialResponse> issueCredential(@PathVariable UUID projectId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.issueCredential(userId, projectId));
    }

    @PostMapping("/projects/{projectId}/credentials/{credentialId}/revoke")
    public ResponseEntity<Void> revokeCredential(@PathVariable UUID projectId, @PathVariable UUID credentialId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        projects.revokeCredential(userId, projectId, credentialId);
        return ResponseEntity.noContent().build();
    }
}
