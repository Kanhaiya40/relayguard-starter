package com.relayguard.identity;

import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final UserAccessTokenService tokens;
    private final UserAccountRepository users;

    public AuthController(AuthService authService, UserAccessTokenService tokens, UserAccountRepository users) {
        this.authService = authService;
        this.tokens = tokens;
        this.users = users;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        IssuedUserAccessCredential issued = authService.login(request);
        return new TokenResponse(issued.credential().getId(), issued.token(), issued.credential().getExpiresAt());
    }

    @PostMapping("/tokens/{credentialId}/revoke")
    public ResponseEntity<Void> revokeToken(@PathVariable UUID credentialId,
            @RequestAttribute(IdentityRequestAttributes.USER_ID) UUID userId) {
        UserAccount user = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!tokens.revoke(user, credentialId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Access token not found");
        }
        return ResponseEntity.noContent().build();
    }
}
