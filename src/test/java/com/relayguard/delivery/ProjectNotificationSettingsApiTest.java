package com.relayguard.delivery;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

@SpringBootTest
@AutoConfigureMockMvc
class ProjectNotificationSettingsApiTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void projectOwnerCanConfigureEmailFailureThreshold() throws Exception {
        TestProject owner = createProject();
        String settings = """
                {"enabled":false,"recipientEmail":"alerts@example.test","failureThreshold":4,
                 "windowMinutes":10,"cooldownMinutes":60}
                """;

        mockMvc.perform(get("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(false))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(put("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$.recipientEmail").value("alerts@example.test"))
                .andExpect(jsonPath("$.failureThreshold").value(4))
                .andExpect(jsonPath("$.windowMinutes").value(10))
                .andExpect(jsonPath("$.cooldownMinutes").value(60));

        mockMvc.perform(get("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failureThreshold").value(4));
        mockMvc.perform(get("/api/v1/projects/{id}/audit", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token()))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].action")
                        .value("NOTIFICATION_SETTINGS_UPDATED"));
    }

    @Test
    void notificationSettingsAreProjectIsolatedAndRequireAuthentication() throws Exception {
        TestProject owner = createProject();
        TestProject other = createProject();
        String settings = """
                {"enabled":false,"recipientEmail":"alerts@example.test","failureThreshold":1,
                 "windowMinutes":5,"cooldownMinutes":30}
                """;
        mockMvc.perform(put("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + other.token()))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + other.token())
                        .contentType(MediaType.APPLICATION_JSON).content(settings))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/projects/{id}/notification-settings", owner.projectId()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidThresholdAndEnablingWhenOperatorHasNoSmtpConfiguration() throws Exception {
        TestProject owner = createProject();
        mockMvc.perform(put("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false,\"recipientEmail\":\"bad\",\"failureThreshold\":0,"
                                + "\"windowMinutes\":5,\"cooldownMinutes\":30}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/v1/projects/{id}/notification-settings", owner.projectId())
                        .header("Authorization", "Bearer " + owner.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true,\"recipientEmail\":\"alerts@example.test\","
                                + "\"failureThreshold\":1,\"windowMinutes\":5,\"cooldownMinutes\":30}"))
                .andExpect(status().isServiceUnavailable());
    }

    private TestProject createProject() throws Exception {
        String accountResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("email", "notify-" + UUID.randomUUID()
                                        + "@example.test", "password", "correct-horse-battery-staple",
                                "workspaceName", "Notifications"))))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode account = objectMapper.readTree(accountResponse);
        String token = account.get("accessToken").asText();
        String projectResponse = mockMvc.perform(post("/api/v1/workspaces/{id}/projects", account.get("workspaceId").asText())
                        .header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"notification-test\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return new TestProject(token, objectMapper.readTree(projectResponse).get("id").asText());
    }

    private record TestProject(String token, String projectId) {}
}
