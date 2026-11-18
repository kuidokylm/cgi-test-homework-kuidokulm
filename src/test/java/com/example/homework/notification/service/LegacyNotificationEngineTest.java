package com.example.homework.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.homework.notification.handler.INotificationHandler;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class LegacyNotificationEngineTest {

  @Test
  void rejectsHandlerWithNullSupportedType() {
    //given
    INotificationHandler handler = mock(INotificationHandler.class);

    //when
    when(handler.supportedType()).thenReturn(null);

    //then
    assertThatThrownBy(
            () -> new LegacyNotificationEngine(mock(JdbcTemplate.class), List.of(handler)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare a non-blank supported type");
  }

  @Test
  void rejectsHandlerWithBlankSupportedType() {
    INotificationHandler handler = mock(INotificationHandler.class);
    when(handler.supportedType()).thenReturn("  ");

    assertThatThrownBy(
            () -> new LegacyNotificationEngine(mock(JdbcTemplate.class), List.of(handler)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("must declare a non-blank supported type");
  }
}
