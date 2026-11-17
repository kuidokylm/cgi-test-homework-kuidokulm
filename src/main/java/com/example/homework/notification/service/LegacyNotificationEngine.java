package com.example.homework.notification.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.homework.notification.handler.INotificationHandler;
import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.handler.EmailNotificationHandler;
import com.example.homework.notification.handler.PushNotificationHandler;
import com.example.homework.notification.handler.SmsNotificationHandler;
import com.example.homework.notification.model.LegacyNotificationResponse;
import com.example.homework.notification.repository.LegacyDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class LegacyNotificationEngine implements InitializingBean {

  private static final Logger logger = LoggerFactory.getLogger(LegacyNotificationEngine.class);
  private static final Pattern EMAIL_ADDRESS =
      Pattern.compile("^[^\\s@]++@(?>[^\\s@]+\\.(?=[^\\s@]))[^\\s@]++$");

  private final Map<String, INotificationHandler> registry = new HashMap<>();
  private final LegacyDatabaseManager dbManager;

  public LegacyNotificationEngine(JdbcTemplate jdbcTemplate) {
    this.dbManager = new LegacyDatabaseManager(jdbcTemplate);
  }

  @Override
  public void afterPropertiesSet() {
    registry.clear();
    registry.put("EMAIL", new EmailNotificationHandler());
    registry.put("SMS", new SmsNotificationHandler());
    registry.put("PUSH", new PushNotificationHandler());
  }

  public void registerHandler(String type, INotificationHandler handler) {
    registry.put(type.trim().toUpperCase(), handler);
  }

  public void unRegisterHandler(String type) {
    registry.remove(type.trim().toUpperCase());
  }

  public synchronized LegacyNotificationResponse processNotification(String type, String target, String payload) {
    String safeTarget = target != null ? target.trim() : null;
    String safeType = type != null ? type.trim().toUpperCase() : "";
    validateTarget(safeTarget, safeType);


    String safePayload = payload != null ? payload : "";

    long id;
    try {
      id = dbManager.openDispatch(safeType, safeTarget, safePayload);
    } catch (Exception e) {
      throw new LegacyNotificationException.DispatchFailed(e);
    }

    if (dbManager.getFailureCountFromDatabase() >= 3) {
      dbManager.markAsSkipped(id, "Circuit breaker OPEN via database state.");
      throw new LegacyNotificationException.CircuitOpen();
    }

    INotificationHandler handler = registry.get(safeType);
    if (handler == null) {
      logger.warn("Unsupported notification type requested: {}", safeType);
      dbManager.recordUnsupportedType(id, safeType);
      throw new LegacyNotificationException.UnsupportedType(safeType);
    }

    try {
      handler.execute(safeTarget, safePayload);
      dbManager.resetDatabaseFailures(id);
      return dbManager.getDispatchById(id);
    } catch (Exception e) {
      dbManager.recordDatabaseFailure(id, safeTarget, e.getMessage());
      throw new LegacyNotificationException.DispatchFailed(e);
    }
  }

  private void validateTarget(String safeTarget, String safeType) {
    if (safeTarget == null || safeTarget.isEmpty()) {
      throw new LegacyNotificationException.InvalidTarget();
    }

    if ("EMAIL".equals(safeType) && !EMAIL_ADDRESS.matcher(safeTarget).matches()) {
      throw new LegacyNotificationException.InvalidEmailTarget();
    }

    if (dbManager.isUserBlacklistedOrRateLimited(safeTarget)) {
      throw new LegacyNotificationException.RecipientRejected();
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

  public void removeFromBlacklist(String target) {
    dbManager.removeFromBlacklist(target);
  }
}