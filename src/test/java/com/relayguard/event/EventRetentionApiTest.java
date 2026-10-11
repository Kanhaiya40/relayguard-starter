package com.relayguard.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayguard.delivery.DeliveryAttempt;
import com.relayguard.delivery.DeliveryAttemptRepository;
import com.relayguard.delivery.DeliveryJob;
import com.relayguard.delivery.DeliveryJobRepository;
import com.relayguard.identity.ProjectCredentialService;
import com.relayguard.identity.ProjectRepository;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EventRetentionApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired DeliveryJobRepository jobs;
    @Autowired DeliveryAttemptRepository attempts;
    @Autowired WebhookEventRepository events;
    @Autowired ProjectRepository projects;
    @Autowired ProjectCredentialService credentials;
    @Autowired EventRetentionService retention;

    @Test
    void anotherWorkspaceCannotDeleteAnEvent() throws Exception {
        TestProject owner = createProject();
        TestProject other = createProject();
        UUID eventId = ingest(projectKey(owner), "private-event");

        mockMvc.perform(delete("/api/v1/projects/{projectId}/events/{eventId}", owner.projectId(), eventId)
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
        assertTrue(events.findById(eventId).isPresent());
    }

    @Test
    void manualDeleteIsAuthorizedAuditedAndRemovesDeliveryHistoryAndExpiredRowsArePurged() throws Exception {
        TestProject project = createProject();
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", project.projectId())
                        .header("Authorization", "Bearer " + project.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://1.1.1.1/retention-test\"}"))
                .andExpect(status().isCreated());
        String projectKey = projectKey(project);

        UUID manualId = ingest(projectKey, "manual-delete-event");
        DeliveryJob job = jobs.findByEvent_Id(manualId).getFirst();
        attempts.save(new DeliveryAttempt(job, 1, Instant.now()));
        mockMvc.perform(delete("/api/v1/projects/{projectId}/events/{eventId}", project.projectId(), manualId)
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isNoContent());
        assertTrue(events.findById(manualId).isEmpty());
        assertTrue(jobs.findByEvent_Id(manualId).isEmpty());
        assertTrue(attempts.findByJob_IdOrderByAttemptNumberAsc(job.getId()).isEmpty());
        String auditJson = mockMvc.perform(get("/api/v1/projects/{projectId}/audit", project.projectId())
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertTrue(objectMapper.readTree(auditJson).toString().contains("EVENT_DELETED"));

        UUID activeId = ingest(projectKey, "active-delete-event");
        DeliveryJob activeJob = jobs.findByEvent_Id(activeId).getFirst();
        activeJob.markInProgress(Instant.now().plusSeconds(60));
        jobs.saveAndFlush(activeJob);
        mockMvc.perform(delete("/api/v1/projects/{projectId}/events/{eventId}", project.projectId(), activeId)
                        .header("Authorization", "Bearer " + project.token()))
                .andExpect(status().isConflict());
        assertTrue(events.findById(activeId).isPresent());

        UUID expiredId = ingest(projectKey, "expired-event");
        DeliveryJob expiredJob = jobs.findByEvent_Id(expiredId).getFirst();
        attempts.saveAndFlush(new DeliveryAttempt(expiredJob, 1, Instant.now()));
        jdbc.update("UPDATE webhook_events SET retention_until = CURRENT_TIMESTAMP - INTERVAL '1' SECOND WHERE id = ?", expiredId);
        assertEquals(1, retention.purgeExpiredBatch());
        assertTrue(events.findById(expiredId).isEmpty());
        assertTrue(jobs.findByEvent_Id(expiredId).isEmpty());
        assertTrue(attempts.findByJob_IdOrderByAttemptNumberAsc(expiredJob.getId()).isEmpty());
    }

    private UUID ingest(String key, String idempotencyKey) throws Exception {
        String response = mockMvc.perform(post("/api/v1/events").header("X-API-Key", key)
                        .header("Idempotency-Key", idempotencyKey).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"retention.test\",\"payload\":{\"safe\":true}}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private String projectKey(TestProject project) {
        return credentials.rotate(projects.findById(UUID.fromString(project.projectId())).orElseThrow()).token();
    }

    private TestProject createProject() throws Exception {
        String email = "retention-" + UUID.randomUUID() + "@example.test";
        JsonNode account = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email,
                                "password", "correct-horse-battery-staple", "workspaceName", "Retention test"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        JsonNode project = objectMapper.readTree(mockMvc.perform(post("/api/v1/workspaces/{id}/projects",
                                account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + account.get("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"retention\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        return new TestProject(account.get("accessToken").asText(), project.get("id").asText());
    }

    private record TestProject(String token, String projectId) {}
}
