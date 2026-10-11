package com.relayguard.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayguard.config.ApiErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class UserAccessTokenFilter extends OncePerRequestFilter {
    private final UserAccessTokenService tokens;
    private final ObjectMapper objectMapper;

    public UserAccessTokenFilter(UserAccessTokenService tokens, ObjectMapper objectMapper) {
        this.tokens = tokens;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.equals("/actuator/health") || path.equals("/api/v1/auth/register")
                || path.equals("/api/v1/auth/login") || path.equals("/api/v1/events")
                || path.startsWith("/api/v1/events/") || !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        String token = authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7) : null;
        var user = tokens.authenticate(token);
        if (user.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(),
                    ApiErrorResponse.of("UNAUTHORIZED", "A valid bearer token is required", 401));
            return;
        }
        request.setAttribute(IdentityRequestAttributes.USER_ID, user.get().getId());
        chain.doFilter(request, response);
    }
}
