package com.relayguard.event;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import com.relayguard.identity.IssuedProjectCredential;
import com.relayguard.identity.Project;
import com.relayguard.identity.ProjectCredentialService;
import com.relayguard.identity.ProjectRepository;
import com.relayguard.identity.Workspace;
import com.relayguard.identity.WorkspaceRepository;
import com.relayguard.delivery.WebhookDestination;
import com.relayguard.delivery.WebhookDestinationRepository;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {
    @Autowired MockMvc mockMvc;
    @Autowired ProjectCredentialService credentialService;
    @Autowired ProjectRepository projectRepository;
    @Autowired WorkspaceRepository workspaceRepository;
    @Autowired WebhookDestinationRepository destinationRepository;

    private String apiKey;
    private Project project;

    @BeforeEach
    void createProjectCredential() {
        Workspace workspace = workspaceRepository.saveAndFlush(new Workspace("event-test-" + UUID.randomUUID()));
        project = projectRepository.saveAndFlush(new Project(workspace, "test-project"));
        IssuedProjectCredential credential = credentialService.issue(project);
        apiKey = credential.token();
    }

    @Test
    void acceptsValidEventAndReturnsIdentifier() throws Exception {
        String response = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{\"orderId\":123}}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.eventType").value("order.created"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"))
                .andReturn().getResponse().getContentAsString();

        UUID eventId = UUID.fromString(new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).get("id").asText());
        mockMvc.perform(get("/api/v1/events/{id}", eventId)
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.eventType").value("order.created"))
                .andExpect(jsonPath("$.status").value("ACCEPTED"));
    }

    @Test
    void rejectsMissingApiKey() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{}}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void rejectsInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/events/00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void validatesRequiredEventType() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\" \",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.timestamp").exists());
    }

    @Test
    void returnsNotFoundForUnknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectsMalformedJsonWithStandardError() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void rejectsEventBodyLargerThanOneMiB() throws Exception {
        String content = "{\"eventType\":\"order.created\",\"payload\":\""
                + "a".repeat(1024 * 1024) + "\"}";
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(content))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"))
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void rejectsMalformedEventIdWithStandardError() throws Exception {
        mockMvc.perform(get("/api/v1/events/not-a-uuid").header("X-API-Key", apiKey))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void filtersEventsByProjectDestinationAndDeliveryState() throws Exception {
        WebhookDestination destination = destinationRepository.saveAndFlush(
                new WebhookDestination(project, "https://1.1.1.1/filter-test"));
        String response = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"filter.test\",\"payload\":{}}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String eventId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response).get("id").asText();

        mockMvc.perform(get("/api/v1/events")
                        .param("destinationId", destination.getId().toString())
                        .param("deliveryStatus", "PENDING")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].id").value(eventId));

        mockMvc.perform(get("/api/v1/events")
                        .param("destinationId", destination.getId().toString())
                        .param("deliveryStatus", "DELIVERED")
                        .header("X-API-Key", apiKey))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void reusesEventForMatchingIdempotencyKeyAndRequest() throws Exception {
        String request = "{\"eventType\":\"order.created\",\"payload\":{\"orderId\":123,\"source\":\"api\"}}";
        String first = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "order-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();
        String duplicate = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "order-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payload\":{\"source\":\"api\",\"orderId\":123},\"eventType\":\"order.created\"}"))
                .andExpect(status().isAccepted())
                .andReturn().getResponse().getContentAsString();

        var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        org.junit.jupiter.api.Assertions.assertEquals(mapper.readTree(first).get("id"), mapper.readTree(duplicate).get("id"));
    }

    @Test
    void rejectsReusedIdempotencyKeyWithDifferentRequest() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "order-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{\"orderId\":123}}"))
                .andExpect(status().isAccepted());

        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "order-conflict")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.updated\",\"payload\":{\"orderId\":123}}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void rejectsBlankIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", " ")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    @Test
    void rejectsIdempotencyKeyLongerThan255Characters() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", apiKey)
                        .header("Idempotency-Key", "k".repeat(256))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\"order.created\",\"payload\":{}}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }
}
