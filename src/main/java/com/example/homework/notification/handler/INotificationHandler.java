package com.example.homework.notification.handler;

import com.example.homework.notification.exception.LegacyNotificationException;

public interface INotificationHandler {
  String supportedType();

  void execute(String target, String payload) throws LegacyNotificationException;
}
