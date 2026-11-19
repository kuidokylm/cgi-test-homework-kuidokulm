package com.example.homework.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.handler.EmailNotificationHandler;
import com.example.homework.notification.service.LegacyNotificationEngine;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyNotificationEngineIntegrationTest {

  private static final String BLACKLIST_QUERY =
      "select exists (select 1 from ntf_blacklist where targetDestination = ?)";

  @Autowired
  private LegacyNotificationEngine engine;

  @Autowired
  private CircuitBreakerRegistry circuitBreakerRegistry;

  @MockitoSpyBean
  private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean
  private EmailNotificationHandler emailHandler;

  @Test
  void allowsRepeatedNotificationsToTheSameRecipient() {
    for (int i = 0; i < 4; i++) {
      engine.processNotification("EMAIL", "repeat-user@example.com", "hello");
    }

    verify(emailHandler, times(4)).execute("repeat-user@example.com", "hello");
  }

  @Test
  void blacklistBlocksDispatchUntilRecipientIsRemoved() {
    engine.insertIntoBlacklist("buyer@example.com");

    assertThrows(
        LegacyNotificationException.RecipientRejected.class,
        () -> engine.processNotification("EMAIL", "buyer@example.com", "hello"));

    engine.removeFromBlacklist("buyer@example.com");
    engine.processNotification("EMAIL", "buyer@example.com", "hello");
    verify(emailHandler).execute("buyer@example.com", "hello");
  }

  @Test
  void invalidAndUnsupportedRequestsDoNotAffectCircuitBreaker() {
    assertThrows(
        LegacyNotificationException.InvalidEmailTarget.class,
        () -> engine.processNotification("EMAIL", "invalid-email", "hello"));
    assertThrows(
        LegacyNotificationException.UnsupportedType.class,
        () -> engine.processNotification("FAX", "ops@example.com", "hello"));

    assertThat(circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
  }

  @Test
  void blacklistDatabaseFailureDoesNotAffectCircuitBreaker() {
    DataAccessResourceFailureException databaseFailure =
        new DataAccessResourceFailureException("Database unavailable");
    doThrow(databaseFailure)
        .when(jdbcTemplate)
        .queryForObject(BLACKLIST_QUERY, Boolean.class, "buyer@example.com");

    assertThat(assertThrows(
        DataAccessResourceFailureException.class,
        () -> engine.processNotification("EMAIL", "buyer@example.com", "hello")))
        .hasCause(databaseFailure);
    assertThat(circuitBreaker().getMetrics().getNumberOfBufferedCalls()).isZero();
  }

  @Test
  void threeConsecutiveHandlerFailuresOpenCircuitBreaker() {
    doThrow(new LegacyNotificationException("SMTP Timeout", HttpStatus.GATEWAY_TIMEOUT, "SMTP Timeout"))
        .when(emailHandler)
        .execute(anyString(), anyString());

    for (int attempt = 0; attempt < 3; attempt++) {
      LegacyNotificationException.DispatchFailed exception = assertThrows(
          LegacyNotificationException.DispatchFailed.class,
          () -> engine.processNotification("EMAIL", "error-user@example.com", "hello"));
      assertThat(exception.getCause()).hasMessage("SMTP Timeout");
    }

    assertThat(circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);
    assertThrows(
        LegacyNotificationException.CircuitOpen.class,
        () -> engine.processNotification("SMS", "ok-user", "hello"));
  }

  @Test
  void breakerUsesThreeCallThresholdAndSixtySecondOpenWait() {
    var config = circuitBreaker().getCircuitBreakerConfig();

    assertThat(config.getSlidingWindowSize()).isEqualTo(3);
    assertThat(config.getMinimumNumberOfCalls()).isEqualTo(3);
    assertThat(config.getFailureRateThreshold()).isEqualTo(100.0f);
    assertThat(config.getWaitIntervalFunctionInOpenState().apply(1)).isEqualTo(60_000L);
    assertThat(config.getPermittedNumberOfCallsInHalfOpenState()).isEqualTo(1);
  }

  private CircuitBreaker circuitBreaker() {
    return circuitBreakerRegistry.circuitBreaker("notificationDispatch");
  }
}
