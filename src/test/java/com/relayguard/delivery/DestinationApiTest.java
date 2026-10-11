package com.relayguard.delivery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayguard.delivery.DeliveryJobRepository;
import com.relayguard.event.WebhookEventRepository;
import com.relayguard.identity.ProjectCredentialService;
import com.relayguard.identity.ProjectRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class DestinationApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DeliveryJobRepository jobs;
    @Autowired WebhookEventRepository events;
    @Autowired ProjectRepository projects;
    @Autowired ProjectCredentialService credentials;

    @Test
    void createsListsAndDisablesDestinationForWorkspaceOwner() throws Exception {
        TestProject project = createProject();
        String destinationBody = objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/webhook",
                "retryMaxAttempts", 5, "retryBaseDelayMs", 2000, "retryMaxDelayMs", 120000));
        String response = mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON).content(destinationBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.retryMaxAttempts").value(5))
                .andExpect(jsonPath("$.retryBaseDelayMs").value(2000))
                .andReturn().getResponse().getContentAsString();
        JsonNode destination = objectMapper.readTree(response);
        mockMvc.perform(get("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(destination.get("id").asText()));
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/projects/{projectId}/destinations/{destinationId}/retry-policy",
                                project.projectId(), destination.get("id").asText())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retryMaxAttempts\":3,\"retryBaseDelayMs\":500,\"retryMaxDelayMs\":30000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.retryMaxAttempts").value(3))
                .andExpect(jsonPath("$.retryBaseDelayMs").value(500));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/destinations/{destinationId}",
                        project.projectId(), destination.get("id").asText())
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
        String auditJson = mockMvc.perform(get("/api/v1/projects/{id}/audit", project.projectId())
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode audit = objectMapper.readTree(auditJson);
        org.junit.jupiter.api.Assertions.assertTrue(java.util.stream.StreamSupport.stream(audit.spliterator(), false)
                .anyMatch(item -> "DESTINATION_DISABLED".equals(item.get("action").asText())));
    }

    @Test
    void rejectsUnsafeDestinationAddressesAndSchemes() throws Exception {
        TestProject project = createProject();
        for (String url : new String[] {
                "http://127.0.0.1/admin", "http://10.2.3.4/", "http://169.254.169.254/latest/meta-data/",
                "http://localhost/", "http://[::1]/", "file:///etc/passwd", "https://user:pass@example.com/"
        }) {
            mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                            .header("Authorization", "Bearer " + project.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("url", url))))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        }
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://1.1.1.1/invalid-retries\",\"retryMaxAttempts\":40}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enforcesTenEnabledDestinationsPerProjectAndAllowsReplacementAfterDisable() throws Exception {
        TestProject project = createProject();
        String firstDestinationId = null;
        for (int i = 0; i < 10; i++) {
            String response = mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                            .header("Authorization", "Bearer " + project.token())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/quota-" + i))))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            if (i == 0) firstDestinationId = objectMapper.readTree(response).get("id").asText();
        }

        mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/quota-over"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DESTINATION_QUOTA_EXCEEDED"));

        mockMvc.perform(delete("/api/v1/projects/{projectId}/destinations/{destinationId}",
                        project.projectId(), firstDestinationId)
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/quota-replacement"))))
                .andExpect(status().isCreated());
    }

    @Test
    void preventsOtherWorkspacesFromManagingDestinations() throws Exception {
        TestProject owner = createProject();
        TestProject other = createProject();
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/hook"))))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/projects/{id}/destinations", owner.projectId())
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
        JsonNode destination = objectMapper.readTree(mockMvc.perform(get("/api/v1/projects/{id}/destinations", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get(0);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch(
                                "/api/v1/projects/{projectId}/destinations/{destinationId}/retry-policy",
                                owner.projectId(), destination.get("id").asText())
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"retryMaxAttempts\":3,\"retryBaseDelayMs\":500,\"retryMaxDelayMs\":30000}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/{id}/audit", owner.projectId())
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", owner.projectId())
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/other"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void atomicallyQueuesOneDeliveryPerDestinationAndDoesNotDuplicateOnIngestRetry() throws Exception {
        TestProject project = createProject();
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/queue-test"))))
                .andExpect(status().isCreated());
        String projectKey = credentials.rotate(projects.findById(UUID.fromString(project.projectId())).orElseThrow()).token();
        String body = "{\"eventType\":\"queue.test\",\"payload\":{\"sample\":true}}";
        String first = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", projectKey).header("Idempotency-Key", "queue-test-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(objectMapper.readTree(first).get("id").asText());

        String retry = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", projectKey).header("Idempotency-Key", "queue-test-1")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();

        org.junit.jupiter.api.Assertions.assertEquals(eventId.toString(), objectMapper.readTree(retry).get("id").asText());
        org.junit.jupiter.api.Assertions.assertEquals(1, jobs.findByEvent_Id(eventId).size());
        org.junit.jupiter.api.Assertions.assertTrue(events.findById(eventId).orElseThrow().getProject() != null);
        mockMvc.perform(get("/api/v1/events/{id}/deliveries", eventId).header("X-API-Key", projectKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].state").value("PENDING"))
                .andExpect(jsonPath("$[0].destinationHost").value("1.1.1.1"))
                .andExpect(jsonPath("$[0].attempts").isEmpty());
    }

    @Test
    void replayCreatesNewJobsPreservesHistoryAndIsAuditedAndIdempotent() throws Exception {
        TestProject project = createProject();
        TestProject other = createProject();
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("url", "https://1.1.1.1/replay-test"))))
                .andExpect(status().isCreated());
        String projectKey = credentials.rotate(projects.findById(UUID.fromString(project.projectId())).orElseThrow()).token();
        String eventJson = mockMvc.perform(post("/api/v1/events").header("X-API-Key", projectKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"replay.test\",\"payload\":{\"sample\":true}}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(objectMapper.readTree(eventJson).get("id").asText());
        int originalJobs = jobs.findByEvent_Id(eventId).size();

        String first = mockMvc.perform(post("/api/v1/projects/{projectId}/events/{eventId}/replays",
                                project.projectId(), eventId)
                        .header("Authorization", "Bearer " + project.token())
                        .header("Idempotency-Key", "replay-request-1"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.replayed").value(false))
                .andExpect(jsonPath("$.deliveryJobIds.length()").value(1))
                .andReturn().getResponse().getContentAsString();

        mockMvc.perform(post("/api/v1/projects/{projectId}/events/{eventId}/replays",
                                project.projectId(), eventId)
                        .header("Authorization", "Bearer " + project.token())
                        .header("Idempotency-Key", "replay-request-1"))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.replayed").value(true));
        mockMvc.perform(get("/api/v1/projects/{projectId}/events/{eventId}/audit", project.projectId(), eventId)
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].action").value("EVENT_REPLAY_REQUESTED"))
                .andExpect(jsonPath("$[0].targetId").value(eventId.toString()));
        mockMvc.perform(post("/api/v1/projects/{projectId}/events/{eventId}/replays",
                                project.projectId(), eventId)
                        .header("Authorization", "Bearer " + other.token())
                        .header("Idempotency-Key", "other-workspace-replay"))
                .andExpect(status().isNotFound());

        org.junit.jupiter.api.Assertions.assertEquals(originalJobs + 1, jobs.findByEvent_Id(eventId).size());
    }

    private TestProject createProject() throws Exception {
        String email = "destination-" + UUID.randomUUID() + "@example.test";
        String accountResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email,
                                "password", "correct-horse-battery-staple", "workspaceName", "Destination test"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode account = objectMapper.readTree(accountResponse);
        String token = account.get("accessToken").asText();
        String projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "destination-test"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return new TestProject(token, objectMapper.readTree(projectResponse).get("id").asText());
    }

    private record TestProject(String token, String projectId) {}
}
