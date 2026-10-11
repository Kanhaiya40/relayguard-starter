package com.relayguard.delivery;

import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectAccessService;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProjectNotificationSettingsService {
    private final ProjectNotificationSettingsRepository settings;
    private final ProjectAccessService access;
    private final AuditEventService audit;
    private final boolean emailEnabled;

    public ProjectNotificationSettingsService(ProjectNotificationSettingsRepository settings,
            ProjectAccessService access, AuditEventService audit,
            @Value("${relayguard.notifications.email.enabled:false}") boolean emailEnabled) {
        this.settings = settings;
        this.access = access;
        this.audit = audit;
        this.emailEnabled = emailEnabled;
    }

    @Transactional(readOnly = true)
    public NotificationSettingsResponse get(UUID userId, UUID projectId) {
        access.requireOwner(userId, projectId);
        return settings.findByProjectId(projectId).map(NotificationSettingsResponse::from)
                .orElseGet(() -> NotificationSettingsResponse.defaults(projectId));
    }

    @Transactional
    public NotificationSettingsResponse update(UUID userId, UUID projectId,
            UpdateNotificationSettingsRequest request) {
        Project project = access.requireOwner(userId, projectId);
        if (request.enabled() && !emailEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Email notifications are not enabled by the operator");
        }
        ProjectNotificationSettings value = settings.findByProjectId(projectId)
                .orElseGet(() -> new ProjectNotificationSettings(project.getId(), request.enabled(),
                        request.recipientEmail(), request.failureThreshold(), request.windowMinutes(),
                        request.cooldownMinutes()));
        value.update(request.enabled(), request.recipientEmail(), request.failureThreshold(),
                request.windowMinutes(), request.cooldownMinutes());
        ProjectNotificationSettings saved = settings.save(value);
        audit.record(project, userId, "NOTIFICATION_SETTINGS_UPDATED", "PROJECT_NOTIFICATION_SETTINGS", projectId);
        return NotificationSettingsResponse.from(saved);
    }
}
