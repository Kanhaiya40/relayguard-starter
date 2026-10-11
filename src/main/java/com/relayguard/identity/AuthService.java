package com.relayguard.identity;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private final UserAccountRepository users;
    private final WorkspaceRepository workspaces;
    private final WorkspaceMembershipRepository memberships;
    private final UserAccessTokenService tokens;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserAccountRepository users, WorkspaceRepository workspaces,
            WorkspaceMembershipRepository memberships, UserAccessTokenService tokens,
            PasswordEncoder passwordEncoder) {
        this.users = users;
        this.workspaces = workspaces;
        this.memberships = memberships;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (users.findByEmail(email).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must be no more than 72 UTF-8 bytes");
        }

        UserAccount user = users.save(new UserAccount(email, passwordEncoder.encode(request.password())));
        Workspace workspace = workspaces.save(new Workspace(request.workspaceName().trim()));
        memberships.save(new WorkspaceMembership(workspace, user, WorkspaceRole.OWNER));
        IssuedUserAccessCredential access = tokens.issue(user);
        return new AuthResponse(user.getId(), workspace.getId(), workspace.getName(),
                access.credential().getId(), access.token(), access.credential().getExpiresAt());
    }

    @Transactional
    public IssuedUserAccessCredential login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw invalidCredentials();
        }
        UserAccount user = users.findByEmail(normalizeEmail(request.email()))
                .orElseThrow(this::invalidCredentials);
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return tokens.issue(user);
    }

    private ResponseStatusException invalidCredentials() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
