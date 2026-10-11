package com.relayguard.identity;

import java.util.List;
import java.util.UUID;
import com.relayguard.event.WorkspaceQuotaService;
import com.relayguard.event.WorkspaceUsageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workspaces")
public class WorkspaceController {
    private final WorkspaceRepository workspaces;
    private final WorkspaceQuotaService quotas;

    public WorkspaceController(WorkspaceRepository workspaces, WorkspaceQuotaService quotas) {
        this.workspaces = workspaces;
        this.quotas = quotas;
    }

    @GetMapping
    public List<WorkspaceResponse> list(@RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return workspaces.findForUser(userId).stream().map(WorkspaceResponse::from).toList();
    }

    @GetMapping("/{workspaceId}/usage")
    public WorkspaceUsageResponse usage(@PathVariable UUID workspaceId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return quotas.getUsage(userId, workspaceId);
    }
}
