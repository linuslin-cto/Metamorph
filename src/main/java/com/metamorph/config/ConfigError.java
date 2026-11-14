package com.metamorph.config;

/** A required deployment value is missing; the message carries the remediation. */
public class ConfigError extends RuntimeException {
  public ConfigError(String message) {
    super(message);
  }

  public ConfigError(String message, Throwable cause) {
    super(message, cause);
  }
}
