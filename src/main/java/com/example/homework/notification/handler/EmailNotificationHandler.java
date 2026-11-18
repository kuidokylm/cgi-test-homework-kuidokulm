package com.example.homework.notification.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class EmailNotificationHandler implements INotificationHandler {

  private static final Logger logger = LoggerFactory.getLogger(EmailNotificationHandler.class);

  @Override
  public String supportedType() {
    return "EMAIL";
  }

  @Override
  public void execute(String target, String payload) {
    logger.info("EMAIL notification dispatched");
  }
}
