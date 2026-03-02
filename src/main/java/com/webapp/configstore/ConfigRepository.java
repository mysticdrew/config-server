package com.webapp.configstore;

import java.util.List;

public interface ConfigRepository {
  List<String> listConfigNames();

  ConfigDocument getConfig(String name);

  void createConfig(String name);

  void putField(String name, String key, ConfigFieldType type, String value);

  void deleteField(String name, String key);

  void deleteConfig(String name);

  String renderProperties(String name);

  String getFieldValue(String name, String key);
}
