package com.webapp.configstore;

public enum ConfigFieldType {
  STRING,
  INTEGER,
  LONG,
  BOOLEAN;

  public static ConfigFieldType fromRaw(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("type must not be blank");
    }
    try {
      return ConfigFieldType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("type must be one of STRING, INTEGER, LONG, BOOLEAN");
    }
  }
}
