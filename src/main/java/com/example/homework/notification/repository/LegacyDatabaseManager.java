package com.example.homework.notification.repository;

import java.sql.Timestamp;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LegacyDatabaseManager {

  private static final Logger logger = LoggerFactory.getLogger(LegacyDatabaseManager.class);

  private final JdbcTemplate jdbcTemplate;

  public LegacyDatabaseManager(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public boolean isBlacklisted(String target) {
    Boolean blacklisted = executeQuery(
        "isBlacklisted",
        () -> jdbcTemplate.queryForObject(
            "select exists (select 1 from ntf_blacklist where targetDestination = ?)",
            Boolean.class,
            target));
    return Boolean.TRUE.equals(blacklisted);
  }

  public void insertIntoBlacklist(String target) {
    executeUpdate(
        "insertIntoBlacklist",
        "insert into ntf_blacklist(targetDestination, created) values (?, ?)",
        target,
        new Timestamp(System.currentTimeMillis()));
    logger.info("Added {} to blacklist", target);
  }

  public void removeFromBlacklist(String target) {
    executeUpdate(
        "removeFromBlacklist",
        "delete from ntf_blacklist where targetDestination = ?",
        target);
    logger.info("Removed {} from blacklist", target);
  }

  private <T> T executeQuery(String operation, Supplier<T> query) {
    try {
      return query.get();
    } catch (DataAccessException e) {
      throw new DataAccessResourceFailureException(
          "Database error while attempting to " + operation + " " + e.getMessage(),
          e);
    }
  }

  private void executeUpdate(String operation, String sql, Object... args) {
    try {
      jdbcTemplate.update(sql, args);
    } catch (DataAccessException e) {
      throw new DataAccessResourceFailureException(
          "Database error while attempting to " + operation + " " + e.getMessage(),
          e);
    }
  }
}
