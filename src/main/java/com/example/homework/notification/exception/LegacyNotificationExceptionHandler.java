package com.example.homework.notification.exception;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.model.LegacyNotificationErrorResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = LegacyNotificationController.class)
public class LegacyNotificationExceptionHandler {

  @ExceptionHandler(LegacyNotificationException.class)
  public ResponseEntity<LegacyNotificationErrorResponse> handleNotificationException(
      LegacyNotificationException exception) {
    return ResponseEntity
        .status(exception.getStatus())
        .body(new LegacyNotificationErrorResponse(exception.getCode(), exception.getMessage()));
  }
}
