package com.webapp.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.webapp.config.AppConfig;
import io.javalin.Javalin;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

class WebServerTest {
  @Test
  void healthEndpointReturnsOk() throws IOException, InterruptedException {
    Javalin app = WebServer.create(new AppConfig(0, "test", tempConfigDir())).start(0);
    try {
      HttpResponse<String> response = sendRequest(app, "/health", "GET", null, null);
      assertEquals(200, response.statusCode(), response.body());
      assertTrue(response.body().contains("\"status\":\"ok\""));
      assertTrue(response.body().contains("\"environment\":\"test\""));
    } finally {
      app.stop();
    }
  }

  @Test
  void configLifecycleAndPropertiesEndpointWork() throws IOException, InterruptedException {
    Javalin app = WebServer.create(new AppConfig(0, "test", tempConfigDir())).start(0);
    try {
      HttpResponse<String> createResponse =
          sendRequest(app, "/api/v1/config", "POST", "{\"name\":\"app-config\"}", null);
      assertEquals(201, createResponse.statusCode(), createResponse.body());

      HttpResponse<String> putFieldResponse =
          sendPutRequest(
              app,
              "/api/v1/config/app-config/fields/retryCount",
              "{\"type\":\"INTEGER\",\"value\":\"5\"}",
              null);
      assertEquals(204, putFieldResponse.statusCode(), putFieldResponse.body());
      HttpResponse<String> putBooleanFieldResponse =
          sendPutRequest(
              app,
              "/api/v1/config/app-config/fields/featureEnabled",
              "{\"type\":\"BOOLEAN\",\"value\":\"true\"}",
              null);
      assertEquals(204, putBooleanFieldResponse.statusCode(), putBooleanFieldResponse.body());

      HttpResponse<String> getJsonResponse =
          sendRequest(app, "/api/v1/config/app-config/json", "GET", null, null);
      assertEquals(200, getJsonResponse.statusCode(), getJsonResponse.body());
      assertTrue(getJsonResponse.body().contains("\"name\":\"app-config\""));
      assertTrue(getJsonResponse.body().contains("\"key\":\"retryCount\""));
      assertTrue(getJsonResponse.body().contains("\"type\":\"INTEGER\""));

      HttpResponse<String> getPropertiesResponse =
          sendRequest(app, "/api/v1/config/app-config", "GET", null, null);
      assertEquals(200, getPropertiesResponse.statusCode(), getPropertiesResponse.body());
      assertTrue(getPropertiesResponse.body().contains("retryCount=5"));
      assertTrue(getPropertiesResponse.body().contains("featureEnabled=true"));

      HttpResponse<String> getSingleValueResponse =
          sendRequest(app, "/api/v1/config/app-config?key=retryCount", "GET", null, null);
      assertEquals(200, getSingleValueResponse.statusCode(), getSingleValueResponse.body());
      assertEquals("5", getSingleValueResponse.body());
    } finally {
      app.stop();
    }
  }

  @Test
  void putFieldValidatesTypeValue() throws IOException, InterruptedException {
    Javalin app = WebServer.create(new AppConfig(0, "test", tempConfigDir())).start(0);
    try {
      sendRequest(app, "/api/v1/config", "POST", "{\"name\":\"app-config\"}", null);
      HttpResponse<String> response =
          sendPutRequest(
              app,
              "/api/v1/config/app-config/fields/featureEnabled",
              "{\"type\":\"BOOLEAN\",\"value\":\"not-boolean\"}",
              null);
      assertEquals(400, response.statusCode(), response.body());
      assertTrue(response.body().contains("\"error\""));
      assertTrue(response.body().contains("\"requestId\""));
    } finally {
      app.stop();
    }
  }

  @Test
  void getSingleValueReturnsNotFoundForMissingField() throws IOException, InterruptedException {
    Javalin app = WebServer.create(new AppConfig(0, "test", tempConfigDir())).start(0);
    try {
      sendRequest(app, "/api/v1/config", "POST", "{\"name\":\"app-config\"}", null);
      HttpResponse<String> response =
          sendRequest(app, "/api/v1/config/app-config?key=doesNotExist", "GET", null, null);
      assertEquals(404, response.statusCode(), response.body());
      assertTrue(response.body().contains("\"error\""));
      assertTrue(response.body().contains("\"requestId\""));
    } finally {
      app.stop();
    }
  }

  @Test
  void requestIdHeaderIsPropagated() throws IOException, InterruptedException {
    Javalin app = WebServer.create(new AppConfig(0, "test", tempConfigDir())).start(0);
    try {
      HttpResponse<String> response = sendRequest(app, "/health", "GET", null, "req-123");
      assertEquals(200, response.statusCode(), response.body());
      assertEquals("req-123", response.headers().firstValue("X-Request-Id").orElse(null));
    } finally {
      app.stop();
    }
  }

  private HttpResponse<String> sendRequest(
      Javalin app, String path, String method, String jsonBody, String requestId)
      throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    String baseUrl = "http://localhost:" + app.port();
    HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path));

    if (requestId != null) {
      builder.header("X-Request-Id", requestId);
    }

    if ("POST".equals(method)) {
      builder.header("Content-Type", "application/json");
      builder.POST(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));
    } else {
      builder.GET();
    }

    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> sendPutRequest(
      Javalin app, String path, String jsonBody, String requestId)
      throws IOException, InterruptedException {
    HttpClient client = HttpClient.newHttpClient();
    String baseUrl = "http://localhost:" + app.port();
    HttpRequest.Builder builder =
        HttpRequest.newBuilder(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(jsonBody == null ? "" : jsonBody));

    if (requestId != null) {
      builder.header("X-Request-Id", requestId);
    }

    return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
  }

  private Path tempConfigDir() {
    try {
      return Files.createTempDirectory("config-test");
    } catch (IOException ex) {
      throw new IllegalStateException("failed to create temp directory", ex);
    }
  }
}
