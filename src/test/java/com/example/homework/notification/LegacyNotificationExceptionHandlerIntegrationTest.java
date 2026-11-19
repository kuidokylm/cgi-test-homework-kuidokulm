package com.example.homework.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.exception.LegacyNotificationExceptionHandler;
import com.example.homework.notification.handler.PushNotificationHandler;
import com.example.homework.notification.handler.SmsNotificationHandler;
import com.example.homework.notification.model.LegacyNotificationErrorResponse;
import com.example.homework.notification.service.LegacyNotificationEngine;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class LegacyNotificationExceptionHandlerIntegrationTest {

  private static final String BLACKLIST_QUERY =
      "select exists (select 1 from ntf_blacklist where targetDestination = ?)";

  @Autowired
  private LegacyNotificationEngine engine;

  @Autowired
  private LegacyNotificationExceptionHandler exceptionHandler;

  @MockitoSpyBean
  private JdbcTemplate jdbcTemplate;

  @MockitoSpyBean
  private SmsNotificationHandler smsHandler;

  @MockitoSpyBean
  private PushNotificationHandler pushHandler;

  @Test
  void mapsDatabaseFailureToServiceUnavailable() throws Exception {
    MockMvc mockMvc = createMockMvc();

    doThrow(new DataAccessResourceFailureException("Database unavailable"))
        .when(jdbcTemplate)
        .queryForObject(BLACKLIST_QUERY, Boolean.class, "database-error@example.com");

    mockMvc.perform(post("/api/legacy/notifications/send")
            .contentType(APPLICATION_JSON)
            .content("""
                {"type":"SMS","target":"database-error@example.com","payload":"hello"}
                """))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
        .andExpect(jsonPath("$.message")
            .value(containsString("Database unavailable")));

    verify(smsHandler, never()).execute("database-error@example.com", "hello");
    verify(pushHandler, never()).execute("database-error@example.com", "hello");
  }

  @Test
  void mapsBlacklistInsertDatabaseFailureToServiceUnavailable() {
    DataAccessResourceFailureException databaseFailure =
        new DataAccessResourceFailureException("Database unavailable");
    doThrow(databaseFailure)
        .when(jdbcTemplate)
        .update(
            eq("insert into ntf_blacklist(targetDestination, created) values (?, ?)"),
            eq("database-error@example.com"),
            any(Timestamp.class));

    DataAccessResourceFailureException wrappedFailure = assertThrows(
        DataAccessResourceFailureException.class,
        () -> engine.insertIntoBlacklist("database-error@example.com"));

    assertThat(wrappedFailure)
        .hasMessageContaining("insertIntoBlacklist")
        .hasCause(databaseFailure);

    var response = exceptionHandler.handleDatabaseFailure(wrappedFailure);
    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    assertThat(response.getBody()).isEqualTo(new LegacyNotificationErrorResponse(
        "DATABASE_UNAVAILABLE",
        "Database is temporarily unavailable. " + wrappedFailure.getMessage()));
  }

  @Test
  void processNotificationDispatchesSmsToSmsHandler() {
    engine.processNotification("SMS", "sms-user", "hello");

    verify(smsHandler).execute("sms-user", "hello");
  }

  @Test
  void processNotificationDispatchesPushToPushHandler() {
    engine.processNotification("PUSH", "push-user", "hello");

    verify(pushHandler).execute("push-user", "hello");
  }

  private MockMvc createMockMvc() {
    return MockMvcBuilders
        .standaloneSetup(new LegacyNotificationController(engine))
        .setControllerAdvice(exceptionHandler)
        .build();
  }
}
