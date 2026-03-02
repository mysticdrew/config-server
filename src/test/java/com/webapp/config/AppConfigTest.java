package com.webapp.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AppConfigTest {
  @Test
  void defaultsToFileStoreWhenNoStoreConfigProvided() {
    AppConfig config = AppConfig.fromValues(Map.of());

    assertEquals(7000, config.port());
    assertEquals("dev", config.environment());
    assertEquals(Path.of("data/configs"), config.configDir());
    assertEquals(ConfigStoreType.FILE, config.configStoreType());
  }

  @Test
  void sqlServerStoreRequiresJdbcUrl() {
    IllegalArgumentException ex =
        assertThrows(
            IllegalArgumentException.class,
            () -> AppConfig.fromValues(Map.of("APP_CONFIG_STORE", "sqlserver")));
    assertEquals(
        "APP_SQLSERVER_JDBC_URL must be set when APP_CONFIG_STORE=sqlserver.", ex.getMessage());
  }

  @Test
  void parsesSqlServerConfigurationFromEnvValues() {
    AppConfig config =
        AppConfig.fromValues(
            Map.of(
                "APP_CONFIG_STORE", "sqlserver",
                "APP_SQLSERVER_JDBC_URL",
                    "jdbc:sqlserver://db.example:1433;databaseName=configdb;encrypt=true",
                "APP_SQLSERVER_USERNAME", "sa",
                "APP_SQLSERVER_PASSWORD", "secret"));

    assertEquals(ConfigStoreType.SQLSERVER, config.configStoreType());
    assertEquals(
        "jdbc:sqlserver://db.example:1433;databaseName=configdb;encrypt=true",
        config.sqlServerJdbcUrl());
    assertEquals("sa", config.sqlServerUsername());
    assertEquals("secret", config.sqlServerPassword());
  }
}
