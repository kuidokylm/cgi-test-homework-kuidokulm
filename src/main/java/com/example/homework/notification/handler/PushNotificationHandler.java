package com.example.homework.notification.handler;

import com.example.homework.notification.INotificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PushNotificationHandler implements INotificationHandler {

  private static final Logger logger = LoggerFactory.getLogger(PushNotificationHandler.class);

  @Override
  public void execute(String target, String payload) {
    logger.info("PUSH notification dispatched");
  }
}
