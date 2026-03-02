package com.webapp.configstore;

public record ConfigField(String key, ConfigFieldType type, String rawValue) {
  public ConfigField {
    if (key == null || key.isBlank()) {
      throw new IllegalArgumentException("key must not be blank");
    }
    if (type == null) {
      throw new IllegalArgumentException("type must not be null");
    }
    if (rawValue == null) {
      throw new IllegalArgumentException("value must not be null");
    }
  }

  public Object typedValue() {
    return switch (type) {
      case STRING -> rawValue;
      case INTEGER -> Integer.parseInt(rawValue);
      case LONG -> Long.parseLong(rawValue);
      case BOOLEAN -> Boolean.parseBoolean(rawValue);
    };
  }
}
