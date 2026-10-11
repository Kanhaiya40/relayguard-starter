package com.relayguard.identity;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccessCredentialRepository extends JpaRepository<UserAccessCredential, UUID> {
    Optional<UserAccessCredential> findByKeyPrefixAndRevokedAtIsNullAndExpiresAtAfter(String keyPrefix, Instant now);
    Optional<UserAccessCredential> findByIdAndUser_IdAndRevokedAtIsNull(UUID id, UUID userId);
}
