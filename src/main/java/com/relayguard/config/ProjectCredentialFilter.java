package com.relayguard.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import com.relayguard.identity.IdentityRequestAttributes;
import com.relayguard.identity.ProjectCredentialService;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class ProjectCredentialFilter extends OncePerRequestFilter {
    private final ProjectCredentialService credentials;
    private final ObjectMapper objectMapper;

    public ProjectCredentialFilter(ProjectCredentialService credentials, ObjectMapper objectMapper) {
        this.credentials = credentials;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.equals("/api/v1/events") && !path.startsWith("/api/v1/events/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String suppliedKey = request.getHeader("X-API-Key");
        var project = credentials.authenticate(suppliedKey);
        if (project.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), ApiErrorResponse.of(
                    "UNAUTHORIZED", "A valid project X-API-Key is required", 401));
            return;
        }
        request.setAttribute(IdentityRequestAttributes.PROJECT_ID, project.get().getId());
        chain.doFilter(request, response);
    }
}
