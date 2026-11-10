package com.example.homework.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class LegacyDatabaseManagerTest {

  private LegacyDatabaseManager databaseManager;

  @BeforeEach
  void setUp() {
    databaseManager = new LegacyDatabaseManager(mock(JdbcTemplate.class));
  }

  @Test
  void shortenReturnsNullWhenValueIsNull() {
    assertThat(invokeShorten(null)).isNull();
  }

  @Test
  void shortenReturnsValuesUpTo255CharactersUnchanged() {
    String value = "x".repeat(255);

    assertThat(invokeShorten(value)).isEqualTo(value);
  }

  @Test
  void shortenTruncatesValuesLongerThan255Characters() {
    String value = "x".repeat(256);

    assertThat(invokeShorten(value)).isEqualTo("x".repeat(255));
  }

  private String invokeShorten(String value) {
    return ReflectionTestUtils.invokeMethod(databaseManager, "shorten", value);
  }
}
