package com.relayguard.identity;

public final class IssuedUserAccessCredential {
    private final UserAccessCredential credential;
    private final String token;

    public IssuedUserAccessCredential(UserAccessCredential credential, String token) {
        this.credential = credential;
        this.token = token;
    }

    public UserAccessCredential credential() { return credential; }
    public String token() { return token; }

    @Override
    public String toString() {
        return "IssuedUserAccessCredential[credential=" + credential.getId() + ", token=[REDACTED]]";
    }
}
