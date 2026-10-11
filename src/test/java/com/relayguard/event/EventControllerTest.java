package com.relayguard.event;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EventControllerTest {
    @Autowired MockMvc mockMvc;

    @Test
    void acceptsValidEventAndReturnsIdentifier() throws Exception {
        String response = mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", "test-api-key")
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
                        .header("X-API-Key", "test-api-key"))
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
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/events/00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", "wrong-key"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void validatesRequiredEventType() throws Exception {
        mockMvc.perform(post("/api/v1/events")
                        .header("X-API-Key", "test-api-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"eventType\":\" \",\"payload\":{}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForUnknownEvent() throws Exception {
        mockMvc.perform(get("/api/v1/events/00000000-0000-0000-0000-000000000000")
                        .header("X-API-Key", "test-api-key"))
                .andExpect(status().isNotFound());
    }
}
