package com.relayguard.delivery;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectNotificationSettingsRepository extends JpaRepository<ProjectNotificationSettings, UUID> {
    Optional<ProjectNotificationSettings> findByProjectId(UUID projectId);
}
