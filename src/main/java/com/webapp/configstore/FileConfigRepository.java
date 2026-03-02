package com.webapp.configstore;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.http.ConflictResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class FileConfigRepository implements ConfigRepository {
  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE = new TypeReference<>() {};
  private final Path configDir;
  private final ObjectMapper objectMapper;

  public FileConfigRepository(Path configDir, ObjectMapper objectMapper) {
    this.configDir = configDir;
    this.objectMapper = objectMapper;
    ensureDirExists();
  }

  @Override
  public List<String> listConfigNames() {
    try (java.util.stream.Stream<Path> paths = Files.list(configDir)) {
      return paths
          .filter(path -> path.getFileName().toString().endsWith(".properties"))
          .map(path -> withoutExtension(path.getFileName().toString(), ".properties"))
          .sorted()
          .toList();
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to list config files");
    }
  }

  @Override
  public ConfigDocument getConfig(String name) {
    Path propsPath = propertiesPath(name);
    if (!Files.exists(propsPath)) {
      throw new NotFoundResponse("config not found");
    }
    Map<String, String> values = loadProperties(name);
    Map<String, String> typeMap = loadTypeMap(name);
    List<ConfigField> fields =
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(
                entry -> {
                  ConfigFieldType type = inferType(typeMap.get(entry.getKey()));
                  return new ConfigField(entry.getKey(), type, entry.getValue());
                })
            .toList();
    return new ConfigDocument(name, fields);
  }

  @Override
  public void createConfig(String name) {
    Path propsPath = propertiesPath(name);
    if (Files.exists(propsPath)) {
      throw new ConflictResponse("config already exists");
    }
    writeStringAtomic(propsPath, "");
    writeTypeMapAtomic(name, Map.of());
  }

  @Override
  public void putField(String name, String key, ConfigFieldType type, String value) {
    ensureConfigExists(name);
    Map<String, String> values = new TreeMap<>(loadProperties(name));
    Map<String, String> typeMap = new TreeMap<>(loadTypeMap(name));
    values.put(key, value);
    typeMap.put(key, type.name());
    writePropertiesAtomic(name, values);
    writeTypeMapAtomic(name, typeMap);
  }

  @Override
  public void deleteField(String name, String key) {
    ensureConfigExists(name);
    Map<String, String> values = new TreeMap<>(loadProperties(name));
    Map<String, String> typeMap = new TreeMap<>(loadTypeMap(name));
    if (!values.containsKey(key)) {
      throw new NotFoundResponse("field not found");
    }
    values.remove(key);
    typeMap.remove(key);
    writePropertiesAtomic(name, values);
    writeTypeMapAtomic(name, typeMap);
  }

  @Override
  public void deleteConfig(String name) {
    Path propsPath = propertiesPath(name);
    if (!Files.exists(propsPath)) {
      throw new NotFoundResponse("config not found");
    }
    try {
      Files.delete(propsPath);
      Files.deleteIfExists(typePath(name));
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to delete config");
    }
  }

  @Override
  public String renderProperties(String name) {
    ensureConfigExists(name);
    Map<String, String> values = loadProperties(name);
    return values.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining("\n"));
  }

  @Override
  public String getFieldValue(String name, String key) {
    ensureConfigExists(name);
    Map<String, String> values = loadProperties(name);
    String value = values.get(key);
    if (value == null) {
      throw new NotFoundResponse("field not found");
    }
    return value;
  }

  private void ensureDirExists() {
    try {
      Files.createDirectories(configDir);
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to initialize config directory");
    }
  }

  private void ensureConfigExists(String name) {
    if (!Files.exists(propertiesPath(name))) {
      throw new NotFoundResponse("config not found");
    }
  }

  private Path propertiesPath(String name) {
    return configDir.resolve(name + ".properties");
  }

  private Path typePath(String name) {
    return configDir.resolve(name + ".types.json");
  }

  private Map<String, String> loadProperties(String name) {
    Path propsPath = propertiesPath(name);
    Properties properties = new Properties();
    try (java.io.Reader reader = Files.newBufferedReader(propsPath, StandardCharsets.UTF_8)) {
      properties.load(reader);
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to read config");
    }
    return properties.entrySet().stream()
        .sorted(Comparator.comparing(entry -> entry.getKey().toString()))
        .collect(
            Collectors.toMap(
                entry -> entry.getKey().toString(),
                entry -> entry.getValue().toString(),
                (left, right) -> right,
                LinkedHashMap::new));
  }

  private Map<String, String> loadTypeMap(String name) {
    Path typesPath = typePath(name);
    if (!Files.exists(typesPath)) {
      return new LinkedHashMap<>();
    }
    try {
      return objectMapper.readValue(typesPath.toFile(), STRING_MAP_TYPE);
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to read config type metadata");
    }
  }

  private void writePropertiesAtomic(String name, Map<String, String> values) {
    String content =
        values.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> entry.getKey() + "=" + escapeValue(entry.getValue()))
            .collect(Collectors.joining("\n"));
    writeStringAtomic(propertiesPath(name), content);
  }

  private void writeTypeMapAtomic(String name, Map<String, String> typeMap) {
    try {
      Path target = typePath(name);
      Path temp = target.resolveSibling(target.getFileName() + ".tmp");
      objectMapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), new TreeMap<>(typeMap));
      moveAtomically(temp, target);
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to write config type metadata");
    }
  }

  private void writeStringAtomic(Path target, String content) {
    try {
      Path temp = target.resolveSibling(target.getFileName() + ".tmp");
      Files.writeString(temp, content, StandardCharsets.UTF_8);
      moveAtomically(temp, target);
    } catch (IOException ex) {
      throw new InternalServerErrorResponse("failed to write config");
    }
  }

  private void moveAtomically(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    } catch (AtomicMoveNotSupportedException ex) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static String withoutExtension(String value, String suffix) {
    return value.substring(0, value.length() - suffix.length());
  }

  private static ConfigFieldType inferType(String rawType) {
    if (rawType == null || rawType.isBlank()) {
      return ConfigFieldType.STRING;
    }
    return ConfigFieldType.fromRaw(rawType);
  }

  private static String escapeValue(String value) {
    return value.replace("\\", "\\\\").replace("\n", "\\n");
  }
}
