package com.example.homework.notification;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
public class LegacyNotificationEngine implements InitializingBean {

  private static final Logger logger = LoggerFactory.getLogger(LegacyNotificationEngine.class);
  private static final Map<String, INotificationHandler> REGISTRY = new HashMap<>();

  private final LegacyDatabaseManager dbManager;

  public LegacyNotificationEngine(JdbcTemplate jdbcTemplate) {
    this.dbManager = new LegacyDatabaseManager(jdbcTemplate);
  }

  @Override
  public void afterPropertiesSet() {
    REGISTRY.clear();
    REGISTRY.put("EMAIL", new EmailHandler());
    REGISTRY.put("SMS", new SmsHandler());
    REGISTRY.put("PUSH", new PushHandler());
  }

  public synchronized LegacyNotificationResponse processNotification(String type, String target, String payload)
      throws Exception {

    String safeType = type != null ? type.trim().toUpperCase() : "";
    String safeTarget = target != null ? target.trim() : null;
    String safePayload = payload != null ? payload : "";

    if (safeTarget == null || safeTarget.isEmpty()) {
      throw new IllegalArgumentException("Target is mandatory.");
    }

    if (dbManager.isUserBlacklistedOrRateLimited(safeTarget)) {
      throw new RuntimeException("Business Rule Violation: User is blacklisted or rate-limited by DB policy.");
    }

    UUID id = dbManager.openDispatch(safeType, safeTarget, safePayload);

    if (dbManager.getFailureCountFromDatabase() >= 3) {
      dbManager.markAsSkipped(id, "Circuit breaker OPEN via database state.");
      throw new RuntimeException("Circuit breaker OPEN via database state.");
    }

    INotificationHandler handler = REGISTRY.get(safeType);
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

  public static class LegacyDatabaseManager {

    private final JdbcTemplate jdbcTemplate;
    private final RowMapper<LegacyNotificationResponse> rowMapper = new RowMapper<>() {
      @Override
      @NullMarked
      public LegacyNotificationResponse mapRow(java.sql.ResultSet rs, int rowNum) throws java.sql.SQLException {
        LegacyNotificationResponse response = new LegacyNotificationResponse();
        response.setId(rs.getObject("id", UUID.class));
        response.setType(rs.getString("notificationType"));
        response.setTarget(rs.getString("targetDestination"));
        response.setPayload(rs.getString("payload"));
        response.setStatus(rs.getString("notificationState"));
        response.setFailureCount(rs.getInt("failureCounter"));
        response.setDispatchCount(rs.getInt("dispatchCounter"));
        response.setBlocked("Y".equals(rs.getString("isBlacklisted")) ? "Y" : "N");
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
      if (target != null && target.toLowerCase().contains("blocked")) {
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

    public UUID openDispatch(final String type, final String target, final String payload) {
      final UUID id = UUID.randomUUID();
      final int nextDispatchCount = getTotalDispatches() + 1;
      final int currentFailureCount = getFailureCountFromDatabase();
      final String blocked = (target != null && target.toLowerCase().contains("blocked")) ? "Y" : "N";
      final Timestamp now = new Timestamp(System.currentTimeMillis());

      jdbcTemplate.update(
          "insert into ntf_tx(id, notificationType, targetDestination, payload, notificationState, "
              + "failureCounter, dispatchCounter, isBlacklisted, lastMessage, created, updated) "
              + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
          id,
          type,
          target,
          payload,
          "N",
          currentFailureCount,
          nextDispatchCount,
          blocked,
          "START",
          now,
          now);

      return id;
    }

    public int getFailureCountFromDatabase() {
      List<Integer> results = jdbcTemplate.query(
          "select failureCounter from ntf_tx order by dispatchCounter desc, id desc limit 1",
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
      UUID id = findLatestId();
      if (id != null) {
        recordDatabaseFailure(id, target, error);
      }
    }

    public void recordDatabaseFailure(UUID id, String target, String error) {
      int nextFailureCount = getFailureCountFromDatabase() + 1;
      jdbcTemplate.update(
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
      UUID id = findLatestId();
      if (id != null) {
        resetDatabaseFailures(id);
      }
    }

    public void resetDatabaseFailures(UUID id) {
      jdbcTemplate.update(
          "update ntf_tx set notificationState = ?, failureCounter = ?, lastMessage = ?, updated = ? where id = ?",
          "S",
          0,
          "SENT",
          new Timestamp(System.currentTimeMillis()),
          id);
    }

    public void recordUnsupportedType(UUID id, String type) {
      jdbcTemplate.update(
          "update ntf_tx set notificationState = ?, lastMessage = ?, updated = ? where id = ?",
          "U",
          shorten("Unsupported type: " + type),
          new Timestamp(System.currentTimeMillis()),
          id);
    }

    public void markAsSkipped(UUID id, String message) {
      jdbcTemplate.update(
          "update ntf_tx set notificationState = ?, lastMessage = ?, updated = ? where id = ?",
          "R",
          shorten(message),
          new Timestamp(System.currentTimeMillis()),
          id);
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
          "select * from ntf_tx order by dispatchCounter desc, id desc limit 1",
          rowMapper);
      if (items.isEmpty()) {
        return null;
      }
      return items.getFirst();
    }

    public LegacyNotificationResponse getDispatchById(UUID id) {
      return jdbcTemplate.queryForObject(
          "select * from ntf_tx where id = ?",
          rowMapper,
          id);
    }

    public List<LegacyNotificationResponse> getHistory() {
      return jdbcTemplate.query(
          "select * from ntf_tx order by dispatchCounter desc, id desc limit 20",
          rowMapper);
    }

    private UUID findLatestId() {
      List<UUID> ids = jdbcTemplate.query(
          "select id from ntf_tx order by dispatchCounter desc, id desc limit 1",
          (resultSet, rowNum) -> resultSet.getObject(1, UUID.class));
      if (ids.isEmpty()) {
        return null;
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

  private interface INotificationHandler {
    void execute(String target, String payload) throws Exception;
  }

  private static class EmailHandler implements INotificationHandler {
    @Override
    public void execute(String target, String payload) throws Exception {
      if (target != null && target.contains("error")) {
        throw new RuntimeException("SMTP Timeout");
      }
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