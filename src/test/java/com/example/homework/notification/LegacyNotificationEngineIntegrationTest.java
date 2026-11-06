package com.example.homework.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.exception.LegacyNotificationExceptionHandler;
import com.example.homework.notification.model.LegacyNotificationResponse;
import com.example.homework.notification.service.LegacyNotificationEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.junit.jupiter.api.Test;

import java.util.List;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LegacyNotificationEngineIntegrationTest {

  @Autowired
  private LegacyNotificationEngine engine;

  @Test
  void emailSuccessIsStoredAsSent() {
    LegacyNotificationResponse response = engine.processNotification("EMAIL", "buyer@example.com", "hello");

    assertThat(response.getStatus()).isEqualTo("S");
    assertThat(response.getDispatchCount()).isEqualTo(1);
    assertThat(response.getFailureCount()).isZero();
    List<LegacyNotificationResponse> historyList = engine.getHistory();
    assertThat(historyList).hasSize(1);
  }

  @Test
  void blacklistEntryBlocksDispatchUntilRemoved() {
    engine.insertIntoBlacklist("buyer@example.com");
    engine.insertIntoBlacklist("testbuyer@example.com");

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("EMAIL", "buyer@example.com", "hello"));
    assertThat(exception).isInstanceOf(LegacyNotificationException.RecipientRejected.class);
    assertThat(engine.getHistory()).isEmpty();

    engine.removeFromblacklist("buyer@example.com");
    LegacyNotificationResponse response =
        engine.processNotification("EMAIL", "buyer@example.com", "hello");
    assertThat(response.getStatus()).isEqualTo("S");
    engine.removeFromblacklist("testbuyer@example.com");
  }

  @Test
  void unsupportedTypeIsStoredAsUnknown() {
    Exception exception = assertThrows(
            Exception.class,
            () -> engine.processNotification("FAX", "ops@example.com", "hello"));
    assertThat(exception.getMessage()).isEqualTo("Unsupported type: FAX");


    LegacyNotificationResponse latest = engine.getLatestState();
    assertThat(latest.getStatus()).isEqualTo("U");
    assertThat(latest.getMessage()).isEqualTo("Unsupported type: FAX");
  }

  @Test
  void threeFailuresOpenCircuitBreakerForNextAttempt() throws Exception {
    INotificationHandler emailHandler = mock(INotificationHandler.class);
    doThrow(new Exception("SMTP Timeout"))
        .when(emailHandler)
        .execute(anyString(), anyString());
    engine.registerHandler("EMAIL", emailHandler);

    for (int i = 0; i < 3; i++) {
      Exception exception = assertThrows(
          Exception.class,
          () -> engine.processNotification("EMAIL", "error-user@example.com", "boom"));
      assertThat(exception).isInstanceOf(LegacyNotificationException.DispatchFailed.class);
      assertThat(exception.getCause().getMessage()).isEqualTo("SMTP Timeout");
    }

    engine.unRegisterHandler("EMAIL");

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("SMS", "ok-user@example.com", "hello"));
    assertThat(exception).isInstanceOf(LegacyNotificationException.CircuitOpen.class);

    LegacyNotificationResponse latest = engine.getLatestState();
    assertThat(latest.getStatus()).isEqualTo("R");
    assertThat(latest.getFailureCount()).isEqualTo(3);
    assertThat(latest.getDispatchCount()).isEqualTo(4);
  }

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