package com.relayguard.identity;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@AutoConfigureMockMvc
class AuthProjectApiTest {
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired UserAccountRepository userRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JdbcTemplate jdbc;

    @Test
    void registersAndLogsInWithHashedPasswordAndBearerToken() throws Exception {
        String email = "signup-" + UUID.randomUUID() + "@example.test";
        JsonNode registration = register(email);
        UserAccount saved = userRepository.findByEmail(email).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotEquals(PASSWORD, saved.getPasswordHash());
        org.junit.jupiter.api.Assertions.assertTrue(passwordEncoder.matches(PASSWORD, saved.getPasswordHash()));
        mockMvc.perform(get("/api/v1/workspaces")
                        .header("Authorization", "Bearer " + registration.get("accessToken").asText()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(registration.get("workspaceId").asText()));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email.toUpperCase(), "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credentialId").exists())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());

        mockMvc.perform(get("/api/v1/workspaces/{id}/projects", registration.get("workspaceId").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void returnsTokenExpiryAndRejectsExpiredBearerTokens() throws Exception {
        JsonNode account = register("expiry-" + UUID.randomUUID() + "@example.test");
        org.junit.jupiter.api.Assertions.assertNotNull(account.get("expiresAt"));
        jdbc.update("UPDATE user_access_credentials SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1' SECOND WHERE id = ?",
                UUID.fromString(account.get("accessTokenId").asText()));

        mockMvc.perform(get("/api/v1/workspaces/{id}/projects", account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + account.get("accessToken").asText()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void scopesProjectsAndEventsToTheirOwningProject() throws Exception {
        JsonNode account = register("projects-" + UUID.randomUUID() + "@example.test");
        String bearer = account.get("accessToken").asText();
        JsonNode firstProject = createProject(account.get("workspaceId").asText(), bearer, "first");
        JsonNode secondProject = createProject(account.get("workspaceId").asText(), bearer, "second");
        String firstKey = issueProjectKey(firstProject.get("id").asText(), bearer);
        String secondKey = issueProjectKey(secondProject.get("id").asText(), bearer);

        String eventBody = "{\"eventType\":\"invoice.created\",\"payload\":{\"invoiceId\":\"inv_1\"}}";
        String accepted = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", firstKey)
                        .header("Idempotency-Key", "invoice-1")
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String eventId = objectMapper.readTree(accepted).get("id").asText();

        mockMvc.perform(get("/api/v1/events/{id}", eventId).header("X-API-Key", secondKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(get("/api/v1/events/{id}", eventId).header("X-API-Key", firstKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId))
                .andExpect(jsonPath("$.payload.invoiceId").value("inv_1"));

        String secondEvent = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", secondKey)
                        .header("Idempotency-Key", "invoice-1")
                        .contentType(MediaType.APPLICATION_JSON).content(eventBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.not(eventId)))
                .andReturn().getResponse().getContentAsString();
        String secondEventId = objectMapper.readTree(secondEvent).get("id").asText();

        mockMvc.perform(get("/api/v1/events?eventType=invoice&page=0&size=1")
                        .header("X-API-Key", firstKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(eventId))
                .andExpect(jsonPath("$.hasNext").value(false));
        mockMvc.perform(get("/api/v1/events?eventType=invoice")
                        .header("X-API-Key", secondKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(secondEventId));
    }

    @Test
    void rejectsCrossWorkspaceProjectAccessAndCredentialRevocation() throws Exception {
        JsonNode owner = register("owner-" + UUID.randomUUID() + "@example.test");
        JsonNode project = createProject(owner.get("workspaceId").asText(),
                owner.get("accessToken").asText(), "owned");
        JsonNode other = register("other-" + UUID.randomUUID() + "@example.test");
        String otherBearer = other.get("accessToken").asText();

        mockMvc.perform(get("/api/v1/workspaces/{id}/projects", owner.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + otherBearer))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/projects/{id}/credentials", project.get("id").asText())
                        .header("Authorization", "Bearer " + otherBearer))
                .andExpect(status().isNotFound());

        JsonNode keyInfo = issueProjectCredential(project.get("id").asText(), owner.get("accessToken").asText());
        mockMvc.perform(post("/api/v1/projects/{projectId}/credentials/{credentialId}/revoke",
                        project.get("id").asText(), keyInfo.get("id").asText())
                        .header("Authorization", "Bearer " + owner.get("accessToken").asText()))
                .andExpect(status().isNoContent());
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", keyInfo.get("token").asText())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"test.event\",\"payload\":{}}"))
                .andExpect(status().isUnauthorized());

        String auditJson = mockMvc.perform(get("/api/v1/projects/{id}/audit", project.get("id").asText())
                        .header("Authorization", "Bearer " + owner.get("accessToken").asText()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode audit = objectMapper.readTree(auditJson);
        java.util.Set<String> actions = new java.util.HashSet<>();
        audit.forEach(item -> actions.add(item.get("action").asText()));
        org.junit.jupiter.api.Assertions.assertTrue(actions.contains("PROJECT_CREDENTIAL_ISSUED"));
        org.junit.jupiter.api.Assertions.assertTrue(actions.contains("PROJECT_CREDENTIAL_REVOKED"));
    }

    @Test
    void refusesDuplicateAccountsAndInvalidPasswords() throws Exception {
        String email = "duplicate-" + UUID.randomUUID() + "@example.test";
        register(email);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email.toUpperCase(), "password", PASSWORD, "workspaceName", "Other"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", "wrong-password"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void revokedUserAccessTokenNoLongerAuthorizesRequests() throws Exception {
        JsonNode account = register("token-revoke-" + UUID.randomUUID() + "@example.test");
        String token = account.get("accessToken").asText();
        mockMvc.perform(post("/api/v1/auth/tokens/{id}/revoke", account.get("accessTokenId").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/workspaces/{id}/projects", account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email, "password", PASSWORD, "workspaceName", "Test workspace"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode createProject(String workspaceId, String token, String name) throws Exception {
        String response = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", workspaceId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("name", name))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private String issueProjectKey(String projectId, String token) throws Exception {
        return issueProjectCredential(projectId, token).get("token").asText();
    }

    private JsonNode issueProjectCredential(String projectId, String token) throws Exception {
        String response = mockMvc.perform(post("/api/v1/projects/{id}/credentials", projectId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }
}
