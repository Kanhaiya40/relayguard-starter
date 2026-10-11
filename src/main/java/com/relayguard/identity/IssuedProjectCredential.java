package com.relayguard.identity;

public final class IssuedProjectCredential {
    private final ProjectCredential credential;
    private final String token;

    IssuedProjectCredential(ProjectCredential credential, String token) {
        this.credential = credential;
        this.token = token;
    }

    public ProjectCredential credential() { return credential; }
    public String token() { return token; }

    @Override
    public String toString() {
        return "IssuedProjectCredential[credential=" + credential.getId() + ", token=[REDACTED]]";
    }
}
