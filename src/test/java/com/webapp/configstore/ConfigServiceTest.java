package com.webapp.configstore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ConfigServiceTest {
  @Test
  void createPutGetAndDeleteFieldFlowPersistsToFiles() {
    ConfigService service = new ConfigService(new FileConfigRepository(tempDir(), new ObjectMapper()));

    service.createConfig("app-a");
    service.putField("app-a", "feature.enabled", "BOOLEAN", "true");
    service.putField("app-a", "retry.count", "INTEGER", "3");

    ConfigDocument document = service.getConfig("app-a");
    assertEquals("app-a", document.name());
    assertEquals(2, document.fields().size());

    String rendered = service.renderProperties("app-a");
    assertTrue(rendered.contains("feature.enabled=true"));
    assertTrue(rendered.contains("retry.count=3"));
    assertEquals("3", service.getFieldValue("app-a", "retry.count"));

    service.deleteField("app-a", "retry.count");
    assertEquals(1, service.getConfig("app-a").fields().size());
  }

  @Test
  void rejectsInvalidValuesByType() {
    ConfigService service = new ConfigService(new FileConfigRepository(tempDir(), new ObjectMapper()));
    service.createConfig("app-a");

    assertThrows(
        IllegalArgumentException.class,
        () -> service.putField("app-a", "max.count", "INTEGER", "abc"));

    assertThrows(
        IllegalArgumentException.class,
        () -> service.putField("app-a", "feature.enabled", "BOOLEAN", "yes"));
  }

  @Test
  void validatesNamesAndKeys() {
    ConfigService service = new ConfigService(new FileConfigRepository(tempDir(), new ObjectMapper()));

    assertThrows(IllegalArgumentException.class, () -> service.createConfig("bad name"));
    service.createConfig("valid-name");
    assertThrows(
        IllegalArgumentException.class,
        () -> service.putField("valid-name", "bad key space", "STRING", "v"));
  }

  private Path tempDir() {
    try {
      return Files.createTempDirectory("config-service");
    } catch (IOException ex) {
      throw new IllegalStateException("failed to create temp dir", ex);
    }
  }
}
