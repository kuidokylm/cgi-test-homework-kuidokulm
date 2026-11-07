package com.example.homework.notification.exception;

import com.example.homework.notification.controller.LegacyNotificationController;
import com.example.homework.notification.model.LegacyNotificationErrorResponse;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
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

  @ExceptionHandler(DataAccessResourceFailureException.class)
  public ResponseEntity<LegacyNotificationErrorResponse> handleDatabaseFailure(
      DataAccessResourceFailureException exception) {
    return ResponseEntity
        .status(HttpStatus.SERVICE_UNAVAILABLE)
        .body(new LegacyNotificationErrorResponse(
            "DATABASE_UNAVAILABLE",
            "Database is temporarily unavailable. "+exception.getMessage()));
  }
}
