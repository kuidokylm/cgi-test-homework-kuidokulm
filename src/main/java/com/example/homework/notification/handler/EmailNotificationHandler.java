package com.example.homework.notification.handler;

import com.example.homework.notification.INotificationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EmailNotificationHandler implements INotificationHandler {

  private static final Logger logger = LoggerFactory.getLogger(EmailNotificationHandler.class);

  @Override
  public void execute(String target, String payload) {
    logger.info("EMAIL notification dispatched");
  }
}
