package com.example.homework.notification.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SmsNotificationHandler implements INotificationHandler {

  private static final Logger logger = LoggerFactory.getLogger(SmsNotificationHandler.class);

  @Override
  public void execute(String target, String payload) {
    logger.info("SMS notification dispatched");
  }
}
