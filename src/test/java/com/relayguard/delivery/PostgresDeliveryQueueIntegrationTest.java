package com.relayguard.delivery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.relayguard.identity.ProjectCredentialService;
import com.relayguard.identity.ProjectRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class PostgresDeliveryQueueIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
        properties.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        properties.add("spring.flyway.enabled", () -> "true");
        properties.add("relayguard.delivery.enabled", () -> "false");
        properties.add("relayguard.notifications.email.enabled", () -> "true");
        properties.add("relayguard.notifications.email.worker-enabled", () -> "false");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired DeliveryQueueService queue;
    @Autowired DeliveryJobRepository jobs;
    @Autowired DeliveryAttemptRepository attempts;
    @Autowired ProjectRepository projects;
    @Autowired ProjectCredentialService credentials;
    @Autowired EmailNotificationOutboxRepository notificationOutbox;
    @Autowired EmailNotificationOutboxService notificationService;

    @Test
    void postgresMigrationsAndSkipLockedQueueCanClaimDeliverAndPersistAttempt() throws Exception {
        String email = "pg-queue-" + UUID.randomUUID() + "@example.test";
        JsonNode account = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email,
                                "password", "correct-horse-battery-staple", "workspaceName", "PG integration"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = account.get("accessToken").asText();
        JsonNode project = objectMapper.readTree(mockMvc.perform(post("/api/v1/workspaces/{id}/projects",
                                account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"queue-test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID projectId = UUID.fromString(project.get("id").asText());
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", projectId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://1.1.1.1/postgres-queue\"}"))
                .andExpect(status().isCreated());
        String key = credentials.rotate(projects.findById(projectId).orElseThrow()).token();
        JsonNode event = objectMapper.readTree(mockMvc.perform(post("/api/v1/events").header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"pg.test\",\"payload\":{\"ok\":true}}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        UUID eventId = UUID.fromString(event.get("id").asText());

        ClaimedDelivery claim = queue.claimNext().orElseThrow();
        assertEquals(eventId, claim.eventId());
        assertEquals(1, claim.attemptNumber());
        queue.complete(claim, new DeliveryResult(204, null, 7));

        DeliveryJob job = jobs.findByEvent_Id(eventId).getFirst();
        assertEquals(DeliveryState.DELIVERED, job.getState());
        assertEquals(1, attempts.findByJob_IdOrderByAttemptNumberAsc(job.getId()).size());
        assertFalse(queue.claimNext().isPresent());
    }

    @Test
    void postgresFailureThresholdQueuesOnePayloadFreeDeduplicatedEmail() throws Exception {
        String email = "pg-alert-" + UUID.randomUUID() + "@example.test";
        JsonNode account = objectMapper.readTree(mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", email,
                                "password", "correct-horse-battery-staple", "workspaceName", "PG alert test"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        String token = account.get("accessToken").asText();
        JsonNode project = objectMapper.readTree(mockMvc.perform(post("/api/v1/workspaces/{id}/projects",
                                account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"alert-test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        UUID projectId = UUID.fromString(project.get("id").asText());
        mockMvc.perform(put("/api/v1/projects/{id}/notification-settings", projectId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"recipientEmail\":\"alerts@example.test\","
                                + "\"failureThreshold\":1,\"windowMinutes\":30,\"cooldownMinutes\":10080}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/projects/{id}/destinations", projectId)
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://1.1.1.1/alert-test\"}"))
                .andExpect(status().isCreated());
        String key = credentials.rotate(projects.findById(projectId).orElseThrow()).token();

        JsonNode firstEvent = objectMapper.readTree(mockMvc.perform(post("/api/v1/events").header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"alert.test\",\"payload\":{\"secret\":\"never-email\"}}"))
                .andExpect(status().isAccepted()).andReturn().getResponse().getContentAsString());
        ClaimedDelivery firstClaim = queue.claimNext().orElseThrow();
        queue.complete(firstClaim, new DeliveryResult(500, "HTTP_500", 7));

        mockMvc.perform(post("/api/v1/events").header("X-API-Key", key)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"alert.test\",\"payload\":{\"attempt\":2}}"))
                .andExpect(status().isAccepted());
        ClaimedDelivery secondClaim = queue.claimNext().orElseThrow();
        queue.complete(secondClaim, new DeliveryResult(500, "HTTP_500", 8));

        UUID firstEventId = UUID.fromString(firstEvent.get("id").asText());
        var notices = notificationOutbox.findByEvent_Id(firstEventId);
        assertEquals(1, notices.size());
        assertEquals(EmailNotificationStatus.PENDING, notices.getFirst().getStatus());
        assertEquals(1, notices.getFirst().getFailureCount());
        assertEquals("alerts@example.test", notices.getFirst().getRecipientEmail());
        assertFalse(notices.getFirst().getDeduplicationKey().contains("secret"));
        var queuedMessage = notificationService.claimNext().orElseThrow();
        assertEquals(firstEventId, queuedMessage.eventId());
        assertEquals("1.1.1.1", queuedMessage.destinationHost());
        assertEquals(1, queuedMessage.attemptCount());
    }
}
