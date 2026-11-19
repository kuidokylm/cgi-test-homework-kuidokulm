package com.example.homework.notification;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.exception.LegacyNotificationExceptionHandler;
import com.example.homework.notification.handler.EmailNotificationHandler;
import com.example.homework.notification.service.LegacyNotificationEngine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyNotificationHttpIntegrationTest {

  @Autowired
  private LegacyNotificationEngine engine;

  @MockitoSpyBean
  private EmailNotificationHandler emailHandler;

  @Test
  void sendEndpointReturnsSuccessWithoutPersistingNotificationHistory() throws Exception {
    MockMvc mockMvc = createMockMvc();

    mockMvc.perform(post("/api/legacy/notifications/send")
            .contentType(APPLICATION_JSON)
            .content("""
                {"type":"EMAIL","target":"http-user@example.com","payload":"hello"}
                """))
        .andExpect(status().isOk())
        .andExpect(content().string(""));

    mockMvc.perform(get("/api/legacy/notifications/history"))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/legacy/notifications/latest"))
        .andExpect(status().isNotFound());
  }

  @Test
  void controllerMapsNotificationExceptionsToHttpStatuses() throws Exception {
    MockMvc mockMvc = createMockMvc();

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
                {"type":"EMAIL","target":"blocked@example.com ","payload":"hello"}
                """))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("RECIPIENT_REJECTED"));

    mockMvc.perform(post("/api/legacy/notifications/send")
            .contentType(APPLICATION_JSON)
            .content("""
                {"type":"EMAIL","payload":"hello"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_TARGET"));

    mockMvc.perform(post("/api/legacy/notifications/send")
            .contentType(APPLICATION_JSON)
            .content("""
                {"target":"helper@example.com ","payload":"hello"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    mockMvc.perform(post("/api/legacy/notifications/send")
            .contentType(APPLICATION_JSON)
            .content("""
                {"target":"missingpayload@example.com "}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"));

    mockMvc.perform(post("/api/legacy/notifications/send")
            .contentType(APPLICATION_JSON)
            .content("""
                {"type":"FAX","target":"unsupportedtype@example.com","payload":"hello"}
                """))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
        .andExpect(jsonPath("$.message").value("Unsupported type: FAX"));

    doThrow(new LegacyNotificationException("SMTP Timeout", HttpStatus.GATEWAY_TIMEOUT, "Notification dispatch failed."))
        .when(emailHandler)
        .execute(anyString(), anyString());

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
                {"type":"SMS","target":"circuit-open","payload":"hello"}
                """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"))
        .andExpect(jsonPath("$.message").value(
            containsString("temporarily unavailable")));

    engine.removeFromBlacklist("blocked@example.com");
  }

  private MockMvc createMockMvc() {
    return MockMvcBuilders
        .standaloneSetup(new LegacyNotificationController(engine))
        .setControllerAdvice(new LegacyNotificationExceptionHandler())
        .build();
  }
}
