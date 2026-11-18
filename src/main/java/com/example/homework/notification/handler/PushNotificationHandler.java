package com.example.homework.notification.handler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PushNotificationHandler implements INotificationHandler {

  private static final Logger logger = LoggerFactory.getLogger(PushNotificationHandler.class);

  @Override
  public String supportedType() {
    return "PUSH";
  }

  @Override
  public void execute(String target, String payload) {
    logger.info("PUSH notification dispatched");
  }
}
