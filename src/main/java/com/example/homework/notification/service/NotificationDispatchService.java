package com.example.homework.notification.service;

import com.example.homework.notification.exception.LegacyNotificationException;
import com.example.homework.notification.handler.INotificationHandler;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.springframework.stereotype.Service;

@Service
public class NotificationDispatchService {

  @CircuitBreaker(name = "notificationDispatch")
  public void execute(INotificationHandler handler, String target, String payload) {
    try {
      handler.execute(target, payload);
    } catch (Exception e) {
      throw new LegacyNotificationException.DispatchFailed(e);
    }
  }
}
