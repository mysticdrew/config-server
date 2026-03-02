package com.webapp.configstore;

import io.javalin.http.ConflictResponse;
import io.javalin.http.InternalServerErrorResponse;
import io.javalin.http.NotFoundResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

public class SqlServerConfigRepository implements ConfigRepository {
  private final String jdbcUrl;
  private final String username;
  private final String password;

  public SqlServerConfigRepository(String jdbcUrl, String username, String password) {
    this.jdbcUrl = jdbcUrl;
    this.username = username;
    this.password = password;
    ensureSchema();
  }

  @Override
  public List<String> listConfigNames() {
    String sql = "SELECT name FROM configs ORDER BY name ASC";
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql);
        ResultSet resultSet = statement.executeQuery()) {
      List<String> names = new ArrayList<>();
      while (resultSet.next()) {
        names.add(resultSet.getString(1));
      }
      return names;
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to list configs");
    }
  }

  @Override
  public ConfigDocument getConfig(String name) {
    ensureConfigExists(name);
    String sql =
        """
        SELECT field_key, field_type, field_value
        FROM config_fields
        WHERE config_name = ?
        ORDER BY field_key ASC
        """;
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      try (ResultSet resultSet = statement.executeQuery()) {
        List<ConfigField> fields = new ArrayList<>();
        while (resultSet.next()) {
          fields.add(
              new ConfigField(
                  resultSet.getString("field_key"),
                  ConfigFieldType.fromRaw(resultSet.getString("field_type")),
                  resultSet.getString("field_value")));
        }
        return new ConfigDocument(name, fields);
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to read config");
    }
  }

  @Override
  public void createConfig(String name) {
    String sql = "INSERT INTO configs(name) VALUES (?)";
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.executeUpdate();
    } catch (SQLException ex) {
      if (isDuplicateKey(ex)) {
        throw new ConflictResponse("config already exists");
      }
      throw new InternalServerErrorResponse("failed to create config");
    }
  }

  @Override
  public void putField(String name, String key, ConfigFieldType type, String value) {
    ensureConfigExists(name);
    String updateSql =
        """
        UPDATE config_fields
        SET field_type = ?, field_value = ?
        WHERE config_name = ? AND field_key = ?
        """;
    String insertSql =
        """
        INSERT INTO config_fields(config_name, field_key, field_type, field_value)
        VALUES (?, ?, ?, ?)
        """;
    try (Connection connection = openConnection();
        PreparedStatement updateStatement = connection.prepareStatement(updateSql);
        PreparedStatement insertStatement = connection.prepareStatement(insertSql)) {
      updateStatement.setString(1, type.name());
      updateStatement.setString(2, value);
      updateStatement.setString(3, name);
      updateStatement.setString(4, key);
      int updatedRows = updateStatement.executeUpdate();
      if (updatedRows == 0) {
        insertStatement.setString(1, name);
        insertStatement.setString(2, key);
        insertStatement.setString(3, type.name());
        insertStatement.setString(4, value);
        insertStatement.executeUpdate();
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to save config field");
    }
  }

  @Override
  public void deleteField(String name, String key) {
    ensureConfigExists(name);
    String sql = "DELETE FROM config_fields WHERE config_name = ? AND field_key = ?";
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setString(2, key);
      int deletedRows = statement.executeUpdate();
      if (deletedRows == 0) {
        throw new NotFoundResponse("field not found");
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to delete field");
    }
  }

  @Override
  public void deleteConfig(String name) {
    String deleteFieldsSql = "DELETE FROM config_fields WHERE config_name = ?";
    String deleteConfigSql = "DELETE FROM configs WHERE name = ?";
    try (Connection connection = openConnection();
        PreparedStatement deleteFields = connection.prepareStatement(deleteFieldsSql);
        PreparedStatement deleteConfig = connection.prepareStatement(deleteConfigSql)) {
      deleteFields.setString(1, name);
      deleteFields.executeUpdate();
      deleteConfig.setString(1, name);
      int deletedRows = deleteConfig.executeUpdate();
      if (deletedRows == 0) {
        throw new NotFoundResponse("config not found");
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to delete config");
    }
  }

  @Override
  public String renderProperties(String name) {
    ensureConfigExists(name);
    return loadFieldValues(name).entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .collect(Collectors.joining("\n"));
  }

  @Override
  public String getFieldValue(String name, String key) {
    ensureConfigExists(name);
    String sql =
        """
        SELECT field_value
        FROM config_fields
        WHERE config_name = ? AND field_key = ?
        """;
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      statement.setString(2, key);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new NotFoundResponse("field not found");
        }
        return resultSet.getString("field_value");
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to read field");
    }
  }

  private void ensureSchema() {
    createTableIfMissing("CREATE TABLE configs (name NVARCHAR(100) PRIMARY KEY)");
    createTableIfMissing(
        """
        CREATE TABLE config_fields (
          config_name NVARCHAR(100) NOT NULL,
          field_key NVARCHAR(100) NOT NULL,
          field_type NVARCHAR(20) NOT NULL,
          field_value NVARCHAR(MAX) NOT NULL,
          CONSTRAINT pk_config_fields PRIMARY KEY (config_name, field_key)
        )
        """);
  }

  private void createTableIfMissing(String sql) {
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.execute();
    } catch (SQLException ex) {
      if (!isTableAlreadyExists(ex)) {
        throw new InternalServerErrorResponse("failed to initialize SQL schema");
      }
    }
  }

  private void ensureConfigExists(String name) {
    String sql = "SELECT 1 FROM configs WHERE name = ?";
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      try (ResultSet resultSet = statement.executeQuery()) {
        if (!resultSet.next()) {
          throw new NotFoundResponse("config not found");
        }
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to read config");
    }
  }

  private Map<String, String> loadFieldValues(String name) {
    String sql =
        """
        SELECT field_key, field_value
        FROM config_fields
        WHERE config_name = ?
        ORDER BY field_key ASC
        """;
    try (Connection connection = openConnection();
        PreparedStatement statement = connection.prepareStatement(sql)) {
      statement.setString(1, name);
      try (ResultSet resultSet = statement.executeQuery()) {
        Map<String, String> values = new TreeMap<>();
        while (resultSet.next()) {
          values.put(resultSet.getString("field_key"), resultSet.getString("field_value"));
        }
        return values;
      }
    } catch (SQLException ex) {
      throw new InternalServerErrorResponse("failed to read config");
    }
  }

  private Connection openConnection() throws SQLException {
    if (username == null || username.isBlank()) {
      return DriverManager.getConnection(jdbcUrl);
    }
    return DriverManager.getConnection(jdbcUrl, username, password);
  }

  private static boolean isDuplicateKey(SQLException ex) {
    String sqlState = ex.getSQLState();
    return "23000".equals(sqlState) || "23505".equals(sqlState);
  }

  private static boolean isTableAlreadyExists(SQLException ex) {
    String sqlState = ex.getSQLState();
    return "42S01".equals(sqlState) || "X0Y32".equals(sqlState);
  }
}
