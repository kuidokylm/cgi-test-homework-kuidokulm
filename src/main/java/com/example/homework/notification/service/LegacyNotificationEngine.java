package com.example.homework.notification.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.homework.notification.handler.INotificationHandler;
import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.model.LegacyNotificationResponse;
import com.example.homework.notification.repository.LegacyDatabaseManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class LegacyNotificationEngine {

  private static final Logger logger = LoggerFactory.getLogger(LegacyNotificationEngine.class);
  private static final Pattern EMAIL_ADDRESS =
      Pattern.compile("^[^\\s@]++@(?>[^\\s@]+\\.(?=[^\\s@]))[^\\s@]++$");

  private final Map<String, INotificationHandler> registry;
  private final LegacyDatabaseManager dbManager;

  public LegacyNotificationEngine(
      LegacyDatabaseManager dbManager, List<INotificationHandler> handlers) {
    this.dbManager = dbManager;
    this.registry = createRegistry(handlers);
  }

  public synchronized LegacyNotificationResponse processNotification(String type, String target, String payload) {
    String safeTarget = target != null ? target.trim() : null;
    String safeType = type != null ? type.trim().toUpperCase(Locale.ROOT) : "";
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

  private static Map<String, INotificationHandler> createRegistry(
      List<INotificationHandler> handlers) {
    Map<String, INotificationHandler> registry = new HashMap<>();
    for (INotificationHandler handler : handlers) {
      String type = handler.supportedType();
      if (type == null || type.isBlank()) {
        throw new IllegalStateException(
            "Notification handler " + handler.getClass().getName()
                + " must declare a non-blank supported type");
      }

      String normalizedType = type.trim().toUpperCase(Locale.ROOT);
      registry.put(normalizedType, handler);
    }
    return Collections.unmodifiableMap(registry);
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