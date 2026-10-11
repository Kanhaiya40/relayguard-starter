package com.relayguard.delivery;

import com.relayguard.identity.Project;
import com.relayguard.identity.UserAccountRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuditEventService {
    private final AuditEventRepository auditEvents;
    private final UserAccountRepository users;

    public AuditEventService(AuditEventRepository auditEvents, UserAccountRepository users) {
        this.auditEvents = auditEvents;
        this.users = users;
    }

    @Transactional
    public AuditEvent record(Project project, UUID actorId, String action, String targetType, UUID targetId) {
        var actor = users.findById(actorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        return auditEvents.save(new AuditEvent(project.getWorkspace(), project, actor,
                action, targetType, targetId, UUID.randomUUID().toString()));
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> forProject(UUID projectId) {
        return auditEvents.findByProject_IdOrderByCreatedAtDesc(projectId, PageRequest.of(0, 100))
                .stream().map(AuditEventResponse::from).toList();
    }
}
