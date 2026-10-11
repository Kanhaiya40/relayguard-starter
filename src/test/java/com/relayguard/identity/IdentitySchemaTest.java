package com.relayguard.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;

@SpringBootTest
class IdentitySchemaTest {
    @Autowired UserAccountRepository userRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WorkspaceMembershipRepository membershipRepository;
    @Autowired ProjectRepository projectRepository;
    @Autowired ProjectCredentialRepository credentialRepository;
    @Autowired ProjectCredentialIssuer credentialIssuer;
    @Autowired ProjectCredentialService credentialService;

    @Test
    void storesProjectCredentialHashAndAllowsPrefixLookup() {
        UserAccount user = userRepository.saveAndFlush(
                new UserAccount("identity-" + java.util.UUID.randomUUID() + "@example.test", "encoded-password-hash"));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("test-workspace"));
        Project project = projectRepository.saveAndFlush(new Project(workspace, "production"));
        membershipRepository.saveAndFlush(new WorkspaceMembership(workspace, user, WorkspaceRole.OWNER));

        IssuedProjectCredential issued = credentialIssuer.issue(project);
        ProjectCredential saved = credentialRepository.saveAndFlush(issued.credential());
        ProjectCredential loaded = credentialRepository
                .findByKeyPrefixAndRevokedAtIsNull(saved.getKeyPrefix()).orElseThrow();

        assertEquals(saved.getCredentialHash(), loaded.getCredentialHash());
        assertNotEquals(issued.token(), loaded.getCredentialHash());
        assertTrue(credentialIssuer.matches(issued.token(), loaded.getCredentialHash()));
    }

    @Test
    void preventsDuplicateMembershipsForTheSameUserAndWorkspace() {
        UserAccount user = userRepository.saveAndFlush(
                new UserAccount("membership-" + java.util.UUID.randomUUID() + "@example.test", "encoded-password-hash"));
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("membership-test"));
        membershipRepository.saveAndFlush(new WorkspaceMembership(workspace, user, WorkspaceRole.OWNER));

        assertThrows(DataIntegrityViolationException.class, () -> membershipRepository.saveAndFlush(
                new WorkspaceMembership(workspace, user, WorkspaceRole.MEMBER)));
    }

    @Test
    void rotatesCredentialsWithOverlapAndRevokesOnlyWithinOwningProject() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("rotation-" + java.util.UUID.randomUUID()));
        Project project = projectRepository.saveAndFlush(new Project(workspace, "primary"));
        Project anotherProject = projectRepository.saveAndFlush(new Project(workspace, "secondary"));

        IssuedProjectCredential initial = credentialService.issue(project);
        IssuedProjectCredential replacement = credentialService.rotate(project);

        ProjectCredential initialStored = credentialRepository.findById(initial.credential().getId()).orElseThrow();
        assertEquals(initial.token().substring(0, 24), initialStored.getKeyPrefix());
        assertTrue(credentialIssuer.matches(initial.token(), initialStored.getCredentialHash()));
        assertEquals(project.getId(), credentialService.authenticate(initial.token()).orElseThrow().getId());
        assertEquals(project.getId(), credentialService.authenticate(replacement.token()).orElseThrow().getId());
        assertFalse(credentialService.revoke(anotherProject, initial.credential().getId()));
        assertTrue(credentialService.revoke(project, initial.credential().getId()));
        assertTrue(credentialService.authenticate(initial.token()).isEmpty());
        assertEquals(project.getId(), credentialService.authenticate(replacement.token()).orElseThrow().getId());
        assertTrue(credentialService.revoke(project, replacement.credential().getId()));
        assertTrue(credentialService.authenticate(replacement.token()).isEmpty());
    }
}
