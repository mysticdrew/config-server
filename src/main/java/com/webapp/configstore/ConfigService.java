package com.webapp.configstore;

import java.util.Locale;
import java.util.regex.Pattern;

public class ConfigService {
  private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");
  private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,100}$");
  private final ConfigRepository repository;

  public ConfigService(ConfigRepository repository) {
    this.repository = repository;
  }

  public java.util.List<String> listConfigNames() {
    return repository.listConfigNames();
  }

  public ConfigDocument getConfig(String name) {
    validateName(name);
    return repository.getConfig(name);
  }

  public void createConfig(String name) {
    validateName(name);
    repository.createConfig(name);
  }

  public void putField(String name, String key, String type, String value) {
    validateName(name);
    validateKey(key);
    ConfigFieldType fieldType = ConfigFieldType.fromRaw(type);
    String normalized = validateAndNormalizeValue(fieldType, value);
    repository.putField(name, key, fieldType, normalized);
  }

  public void deleteField(String name, String key) {
    validateName(name);
    validateKey(key);
    repository.deleteField(name, key);
  }

  public void deleteConfig(String name) {
    validateName(name);
    repository.deleteConfig(name);
  }

  public String renderProperties(String name) {
    validateName(name);
    return repository.renderProperties(name);
  }

  public String getFieldValue(String name, String key) {
    validateName(name);
    validateKey(key);
    return repository.getFieldValue(name, key);
  }

  static void validateName(String name) {
    if (name == null || !NAME_PATTERN.matcher(name).matches()) {
      throw new IllegalArgumentException("config name must match ^[a-zA-Z0-9._-]{1,100}$");
    }
  }

  static void validateKey(String key) {
    if (key == null || !KEY_PATTERN.matcher(key).matches()) {
      throw new IllegalArgumentException("field key must match ^[a-zA-Z0-9._-]{1,100}$");
    }
  }

  static String validateAndNormalizeValue(ConfigFieldType type, String value) {
    if (value == null) {
      throw new IllegalArgumentException("value must not be null");
    }
    return switch (type) {
      case STRING -> value;
      case INTEGER -> Integer.toString(parseInt(value));
      case LONG -> Long.toString(parseLong(value));
      case BOOLEAN -> normalizeBoolean(value);
    };
  }

  private static int parseInt(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("value must be a valid INTEGER", ex);
    }
  }

  private static long parseLong(String value) {
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("value must be a valid LONG", ex);
    }
  }

  private static String normalizeBoolean(String value) {
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (!"true".equals(normalized) && !"false".equals(normalized)) {
      throw new IllegalArgumentException("value must be true or false for BOOLEAN");
    }
    return normalized;
  }
}
