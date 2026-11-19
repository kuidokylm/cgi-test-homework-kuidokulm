package com.example.homework.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import java.sql.Timestamp;

class LegacyDatabaseManagerTest {

  private static final String BLACKLIST_QUERY =
      "select exists (select 1 from ntf_blacklist where targetDestination = ?)";

  private JdbcTemplate jdbcTemplate;
  private LegacyDatabaseManager databaseManager;

  @BeforeEach
  void setUp() {
    jdbcTemplate = mock(JdbcTemplate.class);
    databaseManager = new LegacyDatabaseManager(jdbcTemplate);
  }

  @Test
  void reportsWhetherTargetIsBlacklisted() {
    when(jdbcTemplate.queryForObject(BLACKLIST_QUERY, Boolean.class, "blocked@example.com"))
        .thenReturn(true);

    assertThat(databaseManager.isBlacklisted("blocked@example.com")).isTrue();
  }

  @Test
  void insertsAndRemovesBlacklistEntries() {
    databaseManager.insertIntoBlacklist("blocked@example.com");
    databaseManager.removeFromBlacklist("blocked@example.com");

    verify(jdbcTemplate).update(
        eq("insert into ntf_blacklist(targetDestination, created) values (?, ?)"),
        eq("blocked@example.com"),
        any(Timestamp.class));
    verify(jdbcTemplate).update(
        "delete from ntf_blacklist where targetDestination = ?",
        "blocked@example.com");
  }
}
