package com.example.homework.notification.controller;

import java.util.List;

import com.example.homework.notification.service.LegacyNotificationEngine;
import com.example.homework.notification.model.LegacyNotificationRequest;
import com.example.homework.notification.model.LegacyNotificationResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legacy/notifications")
public class LegacyNotificationController {

  private final LegacyNotificationEngine engine;
  public LegacyNotificationController(LegacyNotificationEngine engine) {
    this.engine = engine;
  }

  @PostMapping(value = "/send")
  public LegacyNotificationResponse send(@RequestBody LegacyNotificationRequest request) throws Exception {
    return engine.processNotification(request.getType(), request.getTarget(), request.getPayload());
  }

  @GetMapping(value = "/history")
  public List<LegacyNotificationResponse> history() {
    return engine.getHistory();
  }

  @GetMapping(value = "/latest")
  public LegacyNotificationResponse latest() {
    return engine.getLatestState();
  }
}