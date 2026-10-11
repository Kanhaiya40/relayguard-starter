package com.relayguard.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayguard.delivery.WorkspaceQuotaAlertRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = "relayguard.quotas.events-per-workspace-month=5")
@AutoConfigureMockMvc
class WorkspaceQuotaApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired WorkspaceQuotaAlertRepository alerts;

    @Test
    void countsAcrossProjectsEnforcesMonthlyCapAndQueuesOneOwnerWarning() throws Exception {
        TestWorkspace account = register("quota-owner-" + UUID.randomUUID() + "@example.test");
        TestProject first = createProject(account, "quota-first");
        TestProject second = createProject(account, "quota-second");

        for (int i = 0; i < 5; i++) {
            TestProject target = i % 2 == 0 ? first : second;
            ingest(target.apiKey(), "quota-event-" + i)
                    .andExpect(status().isAccepted());
        }

        mockMvc.perform(get("/api/v1/workspaces/{id}/usage", account.workspaceId())
                        .header("Authorization", "Bearer " + account.userToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptedEvents").value(5))
                .andExpect(jsonPath("$.eventLimit").value(5))
                .andExpect(jsonPath("$.remainingEvents").value(0))
                .andExpect(jsonPath("$.maxRequestBytes").value(1048576))
                .andExpect(jsonPath("$.maxDestinationsPerProject").value(10))
                .andExpect(jsonPath("$.retentionDays").value(30));

        ingest(first.apiKey(), "quota-event-over")
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("WORKSPACE_EVENT_QUOTA_EXCEEDED"))
                .andExpect(header().exists("Retry-After"));

        assertEquals(1, alerts.findAll().stream()
                .filter(alert -> alert.getWorkspaceId().equals(UUID.fromString(account.workspaceId())))
                .count());
    }

    @Test
    void idempotentReplayDoesNotConsumeMonthlyQuotaTwice() throws Exception {
        TestWorkspace account = register("quota-idempotency-" + UUID.randomUUID() + "@example.test");
        TestProject project = createProject(account, "quota-idempotency");
        String body = "{\"eventType\":\"quota.test\",\"payload\":{\"id\":1}}";
        String first = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", project.apiKey()).header("Idempotency-Key", "same-event")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        String retry = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", project.apiKey()).header("Idempotency-Key", "same-event")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        assertEquals(objectMapper.readTree(first).get("id").asText(), objectMapper.readTree(retry).get("id").asText());

        mockMvc.perform(get("/api/v1/workspaces/{id}/usage", account.workspaceId())
                        .header("Authorization", "Bearer " + account.userToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.acceptedEvents").value(1));
    }

    @Test
    void usageEndpointDoesNotExposeAnotherWorkspace() throws Exception {
        TestWorkspace owner = register("quota-private-" + UUID.randomUUID() + "@example.test");
        TestWorkspace other = register("quota-other-" + UUID.randomUUID() + "@example.test");
        mockMvc.perform(get("/api/v1/workspaces/{id}/usage", owner.workspaceId())
                        .header("Authorization", "Bearer " + other.userToken()))
                .andExpect(status().isNotFound());
    }

    private org.springframework.test.web.servlet.ResultActions ingest(String key, String eventId) throws Exception {
        return mockMvc.perform(post("/api/v1/events")
                .header("X-API-Key", key).header("Idempotency-Key", eventId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventType\":\"quota.test\",\"payload\":{\"id\":\"" + eventId + "\"}}"));
    }

    private TestWorkspace register(String email) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email,
                                "password", "correct-horse-battery-staple", "workspaceName", "Quota test"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode account = objectMapper.readTree(response);
        return new TestWorkspace(account.get("workspaceId").asText(), account.get("accessToken").asText());
    }

    private TestProject createProject(TestWorkspace account, String name) throws Exception {
        String projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", account.workspaceId())
                        .header("Authorization", "Bearer " + account.userToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", name))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String projectId = objectMapper.readTree(projectResponse).get("id").asText();
        String credentialResponse = mockMvc.perform(post("/api/v1/projects/{id}/credentials", projectId)
                        .header("Authorization", "Bearer " + account.userToken()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new TestProject(projectId, objectMapper.readTree(credentialResponse).get("token").asText());
    }

    private record TestWorkspace(String workspaceId, String userToken) {}
    private record TestProject(String projectId, String apiKey) {}
}
