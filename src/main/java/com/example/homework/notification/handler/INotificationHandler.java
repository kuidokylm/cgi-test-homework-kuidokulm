package com.example.homework.notification.handler;

public interface INotificationHandler {
  void execute(String target, String payload) throws Exception;
}
