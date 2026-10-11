package com.relayguard.identity;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class ProjectCredentialIssuer {
    private static final String PREFIX = "rg_live_";
    private final SecureRandom secureRandom = new SecureRandom();
    private final CredentialHash credentialHash;

    public ProjectCredentialIssuer(CredentialHash credentialHash) {
        this.credentialHash = credentialHash;
    }

    public IssuedProjectCredential issue(Project project) {
        byte[] secret = new byte[32];
        secureRandom.nextBytes(secret);
        String token = PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String hash = credentialHash.hash(token);
        String keyPrefix = token.substring(0, 24);
        return new IssuedProjectCredential(new ProjectCredential(project, keyPrefix, hash), token);
    }

    public boolean matches(String suppliedToken, String storedHash) {
        return credentialHash.matches(suppliedToken, storedHash);
    }
}
