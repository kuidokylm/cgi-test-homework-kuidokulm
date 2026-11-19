package com.example.homework.notification.service;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.handler.INotificationHandler;
import com.example.homework.notification.repository.LegacyDatabaseManager;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
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
  private final NotificationDispatchService dispatchService;

  public LegacyNotificationEngine(
      LegacyDatabaseManager dbManager,
      NotificationDispatchService dispatchService,
      List<INotificationHandler> handlers) {
    this.dbManager = dbManager;
    this.dispatchService = dispatchService;
    this.registry = createRegistry(handlers);
  }

  public void processNotification(String type, String target, String payload) {
    String safeTarget = target != null ? target.trim() : null;
    String safeType = type != null ? type.trim().toUpperCase(Locale.ROOT) : "";
    validateTarget(safeTarget, safeType);

    INotificationHandler handler = registry.get(safeType);
    if (handler == null) {
      logger.warn("Unsupported notification type requested: {}", safeType);
      throw new LegacyNotificationException.UnsupportedType(safeType);
    }

    try {
      dispatchService.execute(handler, safeTarget, payload != null ? payload : "");
    } catch (CallNotPermittedException _) {
      logger.error("Notification dispatch failed due to open circuit for type {} and target {}", safeType, safeTarget);
      throw new LegacyNotificationException.CircuitOpen();
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

    if (dbManager.isBlacklisted(safeTarget)) {
      throw new LegacyNotificationException.RecipientRejected();
    }
  }

  public void insertIntoBlacklist(String target) {
    dbManager.insertIntoBlacklist(target);
  }

  public void removeFromBlacklist(String target) {
    dbManager.removeFromBlacklist(target);
  }
}
