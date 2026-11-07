package com.example.homework.notification;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.exception.LegacyNotificationExceptionHandler;
import com.example.homework.notification.handler.INotificationHandler;
import com.example.homework.notification.service.LegacyNotificationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

public class LegacyNotificationHttpIntegrationTest {
    @Autowired
    private LegacyNotificationEngine engine;

    @Test
    void controllerMapsNotificationExceptionsToHttpStatuses() throws Exception {
        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LegacyNotificationController(engine))
                .setControllerAdvice(new LegacyNotificationExceptionHandler())
                .build();

        mockMvc.perform(post("/api/legacy/notifications/send")
                        .contentType(APPLICATION_JSON)
                        .content("""
                {"type":"EMAIL","target":" ","payload":"hello"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_TARGET"));

        engine.insertIntoBlacklist("blocked@example.com");
        mockMvc.perform(post("/api/legacy/notifications/send")
                        .contentType(APPLICATION_JSON)
                        .content("""
                {"type":"EMAIL","target":"blocked@example.com","payload":"hello"}
                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("RECIPIENT_REJECTED"));

        mockMvc.perform(post("/api/legacy/notifications/send")
                        .contentType(APPLICATION_JSON)
                        .content("""
                {"type":"FAX","target":"unsupported@example.com","payload":"hello"}
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        INotificationHandler failingHandler = mock(INotificationHandler.class);
        doThrow(new Exception("SMTP Timeout"))
                .when(failingHandler)
                .execute(anyString(), anyString());
        engine.registerHandler("EMAIL", failingHandler);

        for (int attempt = 1; attempt <= 3; attempt++) {
            mockMvc.perform(post("/api/legacy/notifications/send")
                            .contentType(APPLICATION_JSON)
                            .content("""
                  {"type":"EMAIL","target":"failure-%d@example.com","payload":"hello"}
                  """.formatted(attempt)))
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("DISPATCH_FAILED"))
                    .andExpect(jsonPath("$.message").value("Notification dispatch failed."));
        }

        mockMvc.perform(post("/api/legacy/notifications/send")
                        .contentType(APPLICATION_JSON)
                        .content("""
                {"type":"SMS","target":"circuit-open@example.com","payload":"hello"}
                """))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"));
    }

}
