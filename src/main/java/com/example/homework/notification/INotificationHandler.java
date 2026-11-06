package com.example.homework.notification;

public interface INotificationHandler {
  void execute(String target, String payload) throws Exception;
}
