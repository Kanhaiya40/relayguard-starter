package com.relayguard.event;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "relayguard.rate-limit.events-per-minute=2")
@AutoConfigureMockMvc
class ProjectRateLimitApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void rejectsRequestsAboveProjectMinuteLimitWithRetryAfter() throws Exception {
        String email = "rate-limit-" + UUID.randomUUID() + "@example.test";
        JsonNode account = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email,
                                "password", "correct-horse-battery-staple", "workspaceName", "Rate limit test"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String bearer = account.get("accessToken").asText();
        JsonNode project = objectMapper.readTree(mockMvc.perform(post("/api/v1/workspaces/{id}/projects",
                                account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"limited\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode credential = objectMapper.readTree(mockMvc.perform(post("/api/v1/projects/{id}/credentials",
                                project.get("id").asText())
                        .header("Authorization", "Bearer " + bearer))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());

        for (int request = 1; request <= 2; request++) {
            mockMvc.perform(post("/api/v1/events").header("X-API-Key", credential.get("token").asText())
                            .header("Idempotency-Key", "limit-" + request).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"eventType\":\"limited.event\",\"payload\":{}}"))
                    .andExpect(status().isAccepted());
        }
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        mockMvc.perform(get("/actuator/metrics/relayguard.events.accepted"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("relayguard.events.accepted"))
                .andExpect(jsonPath("$.measurements[0].value").value(org.hamcrest.Matchers.greaterThan(0.0)));
        mockMvc.perform(post("/api/v1/events").header("X-API-Key", credential.get("token").asText())
                        .header("Idempotency-Key", "limit-3").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"limited.event\",\"payload\":{}}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }
}
