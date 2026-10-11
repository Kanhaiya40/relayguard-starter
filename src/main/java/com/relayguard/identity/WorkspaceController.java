package com.relayguard.identity;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceRepository workspaces;

    public WorkspaceController(WorkspaceRepository workspaces) { this.workspaces = workspaces; }

    @GetMapping
    public List<WorkspaceResponse> list(@RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return workspaces.findForUser(userId).stream().map(WorkspaceResponse::from).toList();
    }
}
