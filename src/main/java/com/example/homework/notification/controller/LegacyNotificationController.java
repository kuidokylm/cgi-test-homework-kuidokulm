package com.example.homework.notification.controller;

import com.example.homework.notification.model.LegacyNotificationRequest;
import com.example.homework.notification.service.LegacyNotificationEngine;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legacy/notifications")
public class LegacyNotificationController {

  private final LegacyNotificationEngine engine;
  public LegacyNotificationController(LegacyNotificationEngine engine) {
    this.engine = engine;
  }

  @PostMapping(value = "/send")
  public void send(@RequestBody LegacyNotificationRequest request) {
    engine.processNotification(request.getType(), request.getTarget(), request.getPayload());
  }
}