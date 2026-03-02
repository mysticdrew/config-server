package com.webapp.config;

public enum ConfigStoreType {
  FILE,
  SQLSERVER;

  public static ConfigStoreType fromRaw(String raw) {
    if (raw == null || raw.isBlank()) {
      throw new IllegalArgumentException("APP_CONFIG_STORE must not be blank.");
    }
    try {
      return ConfigStoreType.valueOf(raw.trim().toUpperCase());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException("APP_CONFIG_STORE must be one of: file, sqlserver.");
    }
  }
}
