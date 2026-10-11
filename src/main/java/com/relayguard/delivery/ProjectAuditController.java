package com.relayguard.delivery;

import com.relayguard.identity.IdentityRequestAttributes;
import com.relayguard.identity.ProjectAccessService;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/audit")
public class ProjectAuditController {
    private final ProjectAccessService access;
    private final AuditEventService audit;

    public ProjectAuditController(ProjectAccessService access, AuditEventService audit) {
        this.access = access;
        this.audit = audit;
    }

    @GetMapping
    public List<AuditEventResponse> list(@PathVariable UUID projectId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        access.requireMember(userId, projectId);
        return audit.forProject(projectId);
    }
}
