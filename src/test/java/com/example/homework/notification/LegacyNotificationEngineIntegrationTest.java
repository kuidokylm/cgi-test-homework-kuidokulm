package com.example.homework.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.handler.INotificationHandler;
import com.example.homework.notification.model.LegacyNotificationResponse;
import com.example.homework.notification.service.LegacyNotificationEngine;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.junit.jupiter.api.Test;

import java.util.List;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class LegacyNotificationEngineIntegrationTest {

  private static final String FAILURE_COUNT_QUERY =
      "select failureCounter from ntf_tx order by id desc limit 1";

  @Autowired
  private LegacyNotificationEngine engine;

  @MockitoSpyBean
  private JdbcTemplate jdbcTemplate;

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

    engine.removeFromBlacklist("buyer@example.com");
    LegacyNotificationResponse response =
        engine.processNotification("EMAIL", "buyer@example.com", "hello");
    assertThat(response.getStatus()).isEqualTo("S");
    engine.removeFromBlacklist("testbuyer@example.com");
  }

  @Test
  void invalidEmailTargetIsRejectedBeforeDispatch() {
    //when
    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("EMAIL", "invalid-email", "hello"));

    //then
    assertThat(exception).isInstanceOf(LegacyNotificationException.InvalidEmailTarget.class);
    assertThat(engine.getHistory()).isEmpty();
  }

  @Test
  void databaseFailureWhileGettingFailureCountBlocksDispatchUntilDatabaseRecovers() {
    //given
    doThrow(new DataAccessResourceFailureException("Database error while attempting to"))
        .doCallRealMethod()
        .when(jdbcTemplate)
        .query(eq(FAILURE_COUNT_QUERY), ArgumentMatchers.<RowMapper<Integer>>any());

    //when
    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("EMAIL", "buyer@example.com", "hello"));

    //then
    assertThat(exception).isInstanceOf(LegacyNotificationException.DispatchFailed.class);
    assertThat(exception.getCause()).isInstanceOf(DataAccessResourceFailureException.class);
    assertThat(engine.getHistory()).isEmpty();
  }

  @Test
  void unsupportedTypeIsStoredAsUnknown() {
    //given
    Exception exception = assertThrows(
            Exception.class,
            () -> engine.processNotification("FAX", "ops@example.com", "hello"));
    assertThat(exception.getMessage()).isEqualTo("Unsupported type: FAX");

    //when
    LegacyNotificationResponse latest = engine.getLatestState();

    //then
    assertThat(latest.getStatus()).isEqualTo("U");
    assertThat(latest.getMessage()).isEqualTo("Unsupported type: FAX");
  }

  @Test
  void threeFailuresOpenCircuitBreakerForNextAttempt() throws Exception {
    //given
    INotificationHandler emailHandler = mock(INotificationHandler.class);
    doThrow(new Exception("SMTP Timeout"))
        .when(emailHandler)
        .execute(anyString(), anyString());
    engine.registerHandler("EMAIL", emailHandler);

    //when
    for (int i = 0; i < 3; i++) {
      Exception exception = assertThrows(
          Exception.class,
          () -> engine.processNotification("EMAIL", "error-user@example.com", "boom"));

      //then
      assertThat(exception).isInstanceOf(LegacyNotificationException.DispatchFailed.class);
      assertThat(exception.getCause().getMessage()).isEqualTo("SMTP Timeout");
    }

    engine.unRegisterHandler("EMAIL");
   //when
    RuntimeException exception = assertThrows(
        RuntimeException.class,
        () -> engine.processNotification("SMS", "ok-user@example.com", "hello"));

    //then
    assertThat(exception).isInstanceOf(LegacyNotificationException.CircuitOpen.class);
    LegacyNotificationResponse latest = engine.getLatestState();
    assertThat(latest.getStatus()).isEqualTo("R");
    assertThat(latest.getFailureCount()).isEqualTo(3);
    assertThat(latest.getDispatchCount()).isEqualTo(4);
  }

}