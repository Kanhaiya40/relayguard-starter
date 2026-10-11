package com.relayguard.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectCredentialService {
    private final ProjectCredentialIssuer issuer;
    private final ProjectCredentialRepository repository;

    public ProjectCredentialService(ProjectCredentialIssuer issuer, ProjectCredentialRepository repository) {
        this.issuer = issuer;
        this.repository = repository;
    }

    @Transactional
    public IssuedProjectCredential issue(Project project) {
        IssuedProjectCredential issued = issuer.issue(project);
        ProjectCredential saved = repository.save(issued.credential());
        return new IssuedProjectCredential(saved, issued.token());
    }

    /** Issues a replacement while leaving existing credentials valid for a zero-downtime overlap. */
    @Transactional
    public IssuedProjectCredential rotate(Project project) {
        return issue(project);
    }

    @Transactional(readOnly = true)
    public Optional<Project> authenticate(String token) {
        if (token == null || token.length() < 24) return Optional.empty();
        return repository.findByKeyPrefixAndRevokedAtIsNull(token.substring(0, 24))
                .filter(credential -> issuer.matches(token, credential.getCredentialHash()))
                .map(ProjectCredential::getProject);
    }

    @Transactional
    public boolean revoke(Project project, UUID credentialId) {
        return repository.findByIdAndProject_IdAndRevokedAtIsNull(credentialId, project.getId())
                .map(ProjectCredential::revoke)
                .orElse(false);
    }
}
