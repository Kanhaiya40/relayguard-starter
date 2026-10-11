package com.relayguard.identity;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ProjectCredentialIssuerTest {
    private final ProjectCredentialIssuer issuer = new ProjectCredentialIssuer(new CredentialHash());

    @Test
    void issuesHighEntropyTokenAndStoresOnlyItsHash() {
        Project project = new Project(new Workspace("acme"), "production");
        IssuedProjectCredential issued = issuer.issue(project);

        assertTrue(issued.token().startsWith("rg_live_"));
        assertNotEquals(issued.token(), issued.credential().getCredentialHash());
        assertTrue(issuer.matches(issued.token(), issued.credential().getCredentialHash()));
        assertFalse(issuer.matches("wrong-token", issued.credential().getCredentialHash()));
        assertFalse(issued.toString().contains(issued.token()));
    }
}
