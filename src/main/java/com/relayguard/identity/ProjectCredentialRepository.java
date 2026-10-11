package com.relayguard.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCredentialRepository extends JpaRepository<ProjectCredential, UUID> {
    Optional<ProjectCredential> findByKeyPrefixAndRevokedAtIsNull(String keyPrefix);
    Optional<ProjectCredential> findByIdAndProject_IdAndRevokedAtIsNull(UUID id, UUID projectId);
}
