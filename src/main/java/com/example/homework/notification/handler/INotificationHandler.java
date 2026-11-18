package com.example.homework.notification.handler;

public interface INotificationHandler {
  String supportedType();

  void execute(String target, String payload) throws Exception;
}
