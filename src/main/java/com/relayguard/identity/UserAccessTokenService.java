package com.relayguard.identity;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccessTokenService {
    private static final String PREFIX = "rg_user_";
    private final UserAccessCredentialRepository repository;
    private final CredentialHash credentialHash;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserAccessTokenService(UserAccessCredentialRepository repository, CredentialHash credentialHash) {
        this.repository = repository;
        this.credentialHash = credentialHash;
    }

    @Transactional
    public IssuedUserAccessCredential issue(UserAccount user) {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        UserAccessCredential credential = repository.save(new UserAccessCredential(
                user, token.substring(0, 24), credentialHash.hash(token)));
        return new IssuedUserAccessCredential(credential, token);
    }

    @Transactional(readOnly = true)
    public Optional<UserAccount> authenticate(String token) {
        if (token == null || token.length() < 24) return Optional.empty();
        return repository.findByKeyPrefixAndRevokedAtIsNullAndExpiresAtAfter(token.substring(0, 24), Instant.now())
                .filter(credential -> credentialHash.matches(token, credential.getCredentialHash()))
                .map(UserAccessCredential::getUser);
    }

    @Transactional
    public boolean revoke(UserAccount user, java.util.UUID credentialId) {
        return repository.findByIdAndUser_IdAndRevokedAtIsNull(credentialId, user.getId())
                .map(UserAccessCredential::revoke)
                .orElse(false);
    }
}
