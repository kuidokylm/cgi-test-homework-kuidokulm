package com.example.homework.notification.exception;

import org.springframework.http.HttpStatus;

public class LegacyNotificationException extends RuntimeException {

  private final String code;
  private final HttpStatus status;

  private LegacyNotificationException(String code, HttpStatus status, String message) {
    super(message);
    this.code = code;
    this.status = status;
  }

  private LegacyNotificationException(String code, HttpStatus status, String message, Throwable cause) {
    super(message, cause);
    this.code = code;
    this.status = status;
  }

  public String getCode() {
    return code;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public static final class InvalidTarget extends LegacyNotificationException {
    public InvalidTarget() {
      super("INVALID_TARGET", HttpStatus.BAD_REQUEST, "Target is mandatory.");
    }
  }

  public static final class InvalidEmailTarget extends LegacyNotificationException {
    public InvalidEmailTarget() {
      super("INVALID_TARGET", HttpStatus.BAD_REQUEST, "Email target must be a valid email address.");
    }
  }

  public static final class RecipientRejected extends LegacyNotificationException {
    public RecipientRejected() {
      super(
          "RECIPIENT_REJECTED",
          HttpStatus.FORBIDDEN,
          "Recipient is blacklisted or rate-limited.");
    }
  }

  public static final class CircuitOpen extends LegacyNotificationException {
    public CircuitOpen() {
      super(
          "CIRCUIT_OPEN",
          HttpStatus.SERVICE_UNAVAILABLE,
          "Notification dispatch is temporarily unavailable.");
    }
  }

  public static final class UnsupportedType extends LegacyNotificationException {
    public UnsupportedType(String type) {
      super("BAD_REQUEST", HttpStatus.BAD_REQUEST, "Unsupported type: " + type);
    }
  }

  public static final class DispatchFailed extends LegacyNotificationException {
    public DispatchFailed(Throwable cause) {
      super(
          "DISPATCH_FAILED",
          HttpStatus.BAD_GATEWAY,
          "Notification dispatch failed.",
          cause);
    }
  }
}
