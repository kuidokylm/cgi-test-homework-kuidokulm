package com.example.homework.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class LegacyNotificationEngine implements InitializingBean {

  private static final Logger logger = LoggerFactory.getLogger(LegacyNotificationEngine.class);

  private final Map<String, INotificationHandler> registry = new HashMap<>();
  private final LegacyDatabaseManager dbManager;

  public LegacyNotificationEngine(JdbcTemplate jdbcTemplate) {
    this.dbManager = new LegacyDatabaseManager(jdbcTemplate);
  }

  @Override
  public void afterPropertiesSet() {
    registry.clear();
    registry.put("EMAIL", new EmailHandler());
    registry.put("SMS", new SmsHandler());
    registry.put("PUSH", new PushHandler());
  }

  void registerHandler(String type, INotificationHandler handler) {
    registry.put(type.trim().toUpperCase(), handler);
  }

  void unRegisterHandler(String type) {
    registry.remove(type.trim().toUpperCase());
  }

  public synchronized LegacyNotificationResponse processNotification(String type, String target, String payload)
      throws Exception {
    String safeTarget = target != null ? target.trim() : null;
    if (safeTarget == null || safeTarget.isEmpty()) {
      throw new IllegalArgumentException("Target is mandatory.");
    }

    if (dbManager.isUserBlacklistedOrRateLimited(safeTarget)) {
      throw new RuntimeException("Business Rule Violation: User is blacklisted or rate-limited by DB policy.");
    }
    String safeType = type != null ? type.trim().toUpperCase() : "";
    String safePayload = payload != null ? payload : "";

    long id = dbManager.openDispatch(safeType, safeTarget, safePayload);

    if (dbManager.getFailureCountFromDatabase() >= 3) {
      dbManager.markAsSkipped(id, "Circuit breaker OPEN via database state.");
      throw new RuntimeException("Circuit breaker OPEN via database state.");
    }

    INotificationHandler handler = registry.get(safeType);
    if (handler == null) {
      logger.warn("Unsupported notification type requested: {}", safeType);
      dbManager.recordUnsupportedType(id, safeType);
      throw new Exception("Unsupported type: " + type);
    }

    try {
      handler.execute(safeTarget, safePayload);
      dbManager.resetDatabaseFailures(id);
      return dbManager.getDispatchById(id);
    } catch (Exception e) {
      dbManager.recordDatabaseFailure(id, safeTarget, e.getMessage());
      throw e;
    }
  }

  public List<LegacyNotificationResponse> getHistory() {
    return dbManager.getHistory();
  }

  public LegacyNotificationResponse getLatestState() {
    return dbManager.getLatestState();
  }

  public void insertIntoBlacklist(String target) {
    dbManager.insertIntoBlacklist(target);
  }

  public void removeFromblacklist(String target) {
    dbManager.removeFromBlacklist(target);
  }

  public static class LegacyDatabaseManager {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<LegacyNotificationResponse> rowMapper = new RowMapper<>() {
      @Override
      @NullMarked
      public LegacyNotificationResponse mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        LegacyNotificationResponse response = new LegacyNotificationResponse();
        response.setId(rs.getLong("id"));
        response.setType(rs.getString("notificationType"));
        response.setTarget(rs.getString("targetDestination"));
        response.setPayload(rs.getString("payload"));
        response.setStatus(rs.getString("notificationState"));
        response.setFailureCount(rs.getInt("failureCounter"));
        response.setDispatchCount(rs.getInt("dispatchCounter"));
        response.setMessage(rs.getString("lastMessage"));

        response.setCreatedAt(rs.getTimestamp("created"));
        response.setUpdatedAt(rs.getTimestamp("updated"));
        return response;
      }
    };

    public LegacyDatabaseManager(JdbcTemplate jdbcTemplate) {
      this.jdbcTemplate = jdbcTemplate;
    }

    public boolean isUserBlacklistedOrRateLimited(String target) {

      Boolean blacklisted = jdbcTemplate.queryForObject(
              "select exists (select 1 from ntf_blacklist where targetDestination = ?)",
              Boolean.class,
              target);

      if (Boolean.TRUE.equals(blacklisted)) {
        return true;
      }

      Integer recentCount = jdbcTemplate.queryForObject(
          "select count(*) "
              + "from ntf_tx "
              + "where targetDestination = ? "
              + "  and created >= dateadd('minute', -1, current_timestamp)",
          Integer.class,
          target);

      return recentCount != null && recentCount >= 3;
    }

    public void insertIntoBlacklist(String target) {
      executeUpdate(
          "insertIntoBlacklist",
          "insert into ntf_blacklist(targetDestination, created) values (?, ?)",
          target,
          new Timestamp(System.currentTimeMillis()));
    }

    public void removeFromBlacklist(String target) {
      int deletedRows = executeUpdate(
          "removeFromBlacklist",
          "delete from ntf_blacklist where targetDestination = ?",
          target);
      logger.info("Removed {} blacklist entry/entries", deletedRows);
    }

    public long openDispatch(final String type, final String target, final String payload) {
      final long id = getNextId();
      final int nextDispatchCount = getTotalDispatches() + 1;
      final int currentFailureCount = getFailureCountFromDatabase();
      final Timestamp now = new Timestamp(System.currentTimeMillis());

      executeUpdate(
          "openDispatch",
          "insert into ntf_tx(id, notificationType, targetDestination, payload, notificationState, "
              + "failureCounter, dispatchCounter, lastMessage, created) "
              + "values (?, ?, ?, ?, ?, ?, ?, ?, ?)",
          id,
          type,
          target,
          payload,
          "N",
          currentFailureCount,
          nextDispatchCount,
          "START",
          now);

      return id;
    }

    private long getNextId() {
      Long maxId = jdbcTemplate.queryForObject(
          "select coalesce(max(id), 0) from ntf_tx",
          Long.class);
      return (maxId == null ? 0L : maxId) + 1L;
    }

    public int getFailureCountFromDatabase() {
      List<Integer> results = jdbcTemplate.query(
          "select failureCounter from ntf_tx order by id desc limit 1",
          new RowMapper<>() {
            @Override
            @NullMarked
            public Integer mapRow(ResultSet resultSet, int rowNum) throws SQLException {
              return resultSet.getInt(1);
            }
          });
      if (results.isEmpty()) {
        return 0;
      }
      return results.getFirst();
    }

    public void recordDatabaseFailure(String target, String error) {
      recordDatabaseFailure(findLatestId(), target, error);
    }

    public void recordDatabaseFailure(long id, String target, String error) {
      int nextFailureCount = getFailureCountFromDatabase() + 1;
      executeUpdate(
          "recordDatabaseFailure",
          "update ntf_tx set notificationState = ?, failureCounter = ?, lastMessage = ?, "
              + "updated = ?, targetDestination = ? where id = ?",
          "E",
          nextFailureCount,
          shorten(error),
          new Timestamp(System.currentTimeMillis()),
          target,
          id);
      logger.warn("Recorded failure for notification dispatch {}: {}", id, error);
    }

    public void resetDatabaseFailures() {
      long id = findLatestId();
      if (id > 0L) {
        resetDatabaseFailures(id);
      }
    }

    public void resetDatabaseFailures(long id) {
      executeUpdate(
          "resetDatabaseFailures",
          "update ntf_tx set notificationState = ?, failureCounter = ?, lastMessage = ?, updated = ? where id = ?",
          "S",
          0,
          "SENT",
          new Timestamp(System.currentTimeMillis()),
          id);
    }

    public void recordUnsupportedType(long id, String type) {
      executeUpdate(
          "recordUnsupportedType",
          "update ntf_tx set notificationState = ?, lastMessage = ?, updated = ? where id = ?",
          "U",
          shorten("Unsupported type: " + type),
          new Timestamp(System.currentTimeMillis()),
          id);
    }

    public void markAsSkipped(long id, String message) {
      executeUpdate(
          "markAsSkipped",
          "update ntf_tx set notificationState = ?, lastMessage = ?, updated = ? where id = ?",
          "R",
          shorten(message),
          new Timestamp(System.currentTimeMillis()),
          id);
    }

    private int executeUpdate(String operation, String sql, Object... args) {
      try {
        return jdbcTemplate.update(sql, args);
      } catch (DataAccessException e) {
        logger.error("Database error while attempting to {}.", operation, e);
        throw e;
      }
    }

    public int getTotalDispatches() {
      Integer latestDispatchCount = jdbcTemplate.queryForObject(
          "select coalesce(max(dispatchCounter), 0) from ntf_tx",
          Integer.class);
      if (latestDispatchCount == null) {
        return 0;
      }
      return latestDispatchCount;
    }

    public LegacyNotificationResponse getLatestState() {
      List<LegacyNotificationResponse> items = jdbcTemplate.query(
          "select * from ntf_tx order by id desc limit 1",
          rowMapper);
      if (items.isEmpty()) {
        return null;
      }
      return items.getFirst();
    }

    public LegacyNotificationResponse getDispatchById(long id) {
      return jdbcTemplate.queryForObject(
          "select * from ntf_tx where id = ?",
          rowMapper,
          id);
    }

    public List<LegacyNotificationResponse> getHistory() {
      return jdbcTemplate.query(
          "select * from ntf_tx order by id desc limit 20",
          rowMapper);
    }

    private long findLatestId() {
      List<Long> ids = jdbcTemplate.query(
          "select id from ntf_tx order by id desc limit 1",
          new RowMapper<>() {
            @Override
            @NullMarked
            public Long mapRow(ResultSet resultSet, int rowNum) throws SQLException {
              return resultSet.getLong(1);
            }
          });
      if (ids.isEmpty()) {
        return 0L;
      }
      return ids.getFirst();
    }

    private String shorten(String value) {
      if (value == null) {
        return null;
      }
      if (value.length() <= 255) {
        return value;
      }
      return value.substring(0, 255);
    }
  }

  interface INotificationHandler {
    void execute(String target, String payload) throws Exception;
  }

  private static class EmailHandler implements INotificationHandler {
    @Override
    public void execute(String target, String payload) {
      logger.info("EMAIL notification dispatched");
    }
  }

  private static class SmsHandler implements INotificationHandler {
    @Override
    public void execute(String target, String payload) {
      logger.info("SMS notification dispatched");
    }
  }

  private static class PushHandler implements INotificationHandler {
    @Override
    public void execute(String target, String payload) {
      logger.info("PUSH notification dispatched");
    }
  }
}