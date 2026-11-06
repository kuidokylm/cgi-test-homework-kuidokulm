package com.example.homework.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.junit.jupiter.api.Test;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LegacyNotificationEngineIntegrationTest {

  @Autowired
  private LegacyNotificationEngine engine;

  @Test
  void emailSuccessIsStoredAsSent() throws Exception {
    LegacyNotificationResponse response = engine.processNotification("EMAIL", "buyer@example.com", "hello");

    assertThat(response.getStatus()).isEqualTo("S");
    assertThat(response.getDispatchCount()).isEqualTo(1);
    assertThat(response.getFailureCount()).isZero();
    assertThat(engine.getHistory()).hasSize(1);
  }

  @Test
  void blockedTargetFailsBeforeDispatchIsInserted() {
    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("SMS", "blocked-user@example.com", "hello"));
    assertThat(exception.getMessage())
        .isEqualTo("Business Rule Violation: User is blacklisted or rate-limited by DB policy.");

    assertThat(engine.getHistory()).isEmpty();
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
  void threeFailuresOpenCircuitBreakerForNextAttempt() {
    for (int i = 0; i < 3; i++) {
      Exception exception = assertThrows(
          Exception.class,
          () -> engine.processNotification("EMAIL", "error-user@example.com", "boom"));
      assertThat(exception.getMessage()).isEqualTo("SMTP Timeout");
    }

    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("SMS", "ok-user@example.com", "hello"));
    assertThat(exception.getMessage()).isEqualTo("Circuit breaker OPEN via database state.");

    LegacyNotificationResponse latest = engine.getLatestState();
    assertThat(latest.getStatus()).isEqualTo("R");
    assertThat(latest.getFailureCount()).isEqualTo(3);
    assertThat(latest.getDispatchCount()).isEqualTo(4);
  }
}