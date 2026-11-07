package com.example.homework.notification;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.exception.LegacyNotificationExceptionHandler;
import com.example.homework.notification.handler.INotificationHandler;
import com.example.homework.notification.service.LegacyNotificationEngine;
import org.mockito.ArgumentMatchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)

public class LegacyNotificationHttpIntegrationTest {
    private static final String HISTORY_QUERY = "select * from ntf_tx order by id desc limit 20";

    @Autowired
    private LegacyNotificationEngine engine;

    @MockitoSpyBean
    private JdbcTemplate jdbcTemplate;

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
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
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
                {"type":"FAX","target":"unsupportedtype@example.com","payload":"hello"}
                """))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Unsupported type: FAX"));

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
                    .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                    .andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("DISPATCH_FAILED"))
                    .andExpect(jsonPath("$.message").value("Notification dispatch failed."));
        }

        mockMvc.perform(post("/api/legacy/notifications/send")
                        .contentType(APPLICATION_JSON)
                        .content("""
                {"type":"SMS","target":"circuit-open@example.com","payload":"hello"}
                """))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("CIRCUIT_OPEN"))
                .andExpect(jsonPath("$.message").value("Notification dispatch is temporarily unavailable."));
    }


    @Test
    void historyDatabaseFailureMapsToServiceUnavailableResponse() throws Exception {
        //given
        doThrow(new DataAccessResourceFailureException("Database unavailable"))
                .when(jdbcTemplate)
                .query(eq(HISTORY_QUERY), ArgumentMatchers.<RowMapper<?>>any());

        MockMvc mockMvc = MockMvcBuilders
                .standaloneSetup(new LegacyNotificationController(engine))
                .setControllerAdvice(new LegacyNotificationExceptionHandler())
                .build();

        //when
        mockMvc.perform(get("/api/legacy/notifications/history"))
                .andDo(org.springframework.test.web.servlet.result.MockMvcResultHandlers.print())
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("DATABASE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value(
                        containsString("Database error while attempting to getHistory")))
                .andReturn();
    }
}
