package com.relayguard.delivery;

import com.relayguard.identity.IdentityRequestAttributes;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/projects/{projectId}/notification-settings")
public class ProjectNotificationSettingsController {
    private final ProjectNotificationSettingsService service;

    public ProjectNotificationSettingsController(ProjectNotificationSettingsService service) {
        this.service = service;
    }

    @GetMapping
    public NotificationSettingsResponse get(@PathVariable UUID projectId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        return service.get(userId, projectId);
    }

    @PutMapping
    public NotificationSettingsResponse update(@PathVariable UUID projectId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId,
            @Valid @RequestBody UpdateNotificationSettingsRequest request) {
        return service.update(userId, projectId, request);
    }
}
