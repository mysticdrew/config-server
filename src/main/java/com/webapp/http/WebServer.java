package com.webapp.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.webapp.api.config.ConfigApi;
import com.webapp.config.AppConfig;
import com.webapp.configstore.ConfigService;
import com.webapp.configstore.FileConfigRepository;
import io.javalin.Javalin;
import io.javalin.http.HttpResponseException;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public final class WebServer {
  private static final Logger logger = LoggerFactory.getLogger(WebServer.class);
  private static final String REQUEST_ID_HEADER = "X-Request-Id";
  private static final String REQUEST_ID_CONTEXT_KEY = "requestId";
  private static final String REQUEST_START_NANOS = "requestStartNanos";

  private WebServer() {}

  public static Javalin create(AppConfig config) {
    ConfigService configService =
        new ConfigService(new FileConfigRepository(config.configDir(), new ObjectMapper()));
    ConfigApi configApi = new ConfigApi(configService);

    return Javalin.create(
        javalinConfig -> {
          javalinConfig.staticFiles.add("/public");

          javalinConfig.routes.before(
              ctx -> {
                String requestId = requestIdFromHeader(ctx.header(REQUEST_ID_HEADER));
                ctx.attribute(REQUEST_ID_CONTEXT_KEY, requestId);
                ctx.attribute(REQUEST_START_NANOS, System.nanoTime());
                ctx.header(REQUEST_ID_HEADER, requestId);
                MDC.put(REQUEST_ID_CONTEXT_KEY, requestId);
              });

          javalinConfig.routes.after(
              ctx -> {
                Long startedAtNanosAttr = ctx.attribute(REQUEST_START_NANOS);
                long startedAtNanos = startedAtNanosAttr == null ? 0L : startedAtNanosAttr;
                long elapsedMillis =
                    startedAtNanos == 0L ? 0L : (System.nanoTime() - startedAtNanos) / 1_000_000L;
                logger.info(
                    "request method={} path={} status={} durationMs={}",
                    ctx.method(),
                    ctx.path(),
                    ctx.statusCode(),
                    elapsedMillis);
                MDC.clear();
              });

          javalinConfig.routes.exception(
              IllegalArgumentException.class,
              (ex, ctx) ->
                  ctx.status(400).json(new ErrorResponse(ex.getMessage(), requestId(ctx))));

          javalinConfig.routes.exception(
              HttpResponseException.class,
              (ex, ctx) -> {
                int status = ex.getStatus();
                String message =
                    ex.getMessage() == null || ex.getMessage().isBlank()
                        ? "request failed"
                        : ex.getMessage();
                ctx.status(status).json(new ErrorResponse(message, requestId(ctx)));
              });

          javalinConfig.routes.exception(
              Exception.class,
              (ex, ctx) -> {
                logger.error("Unhandled error while processing request", ex);
                ctx.status(500).json(new ErrorResponse("internal server error", requestId(ctx)));
              });

          javalinConfig.routes.get(
              "/health",
              ctx -> ctx.json(Map.of("status", "ok", "environment", config.environment())));

          javalinConfig.routes.get("/api/v1/config", configApi.listConfigs());
          javalinConfig.routes.post("/api/v1/config", configApi.createConfig());
          javalinConfig.routes.get("/api/v1/config/{name}/json", configApi.getConfigJson());
          javalinConfig.routes.get("/api/v1/config/{name}", configApi.getConfigProperties());
          javalinConfig.routes.put("/api/v1/config/{name}/fields/{key}", configApi.putField());
          javalinConfig.routes.delete(
              "/api/v1/config/{name}/fields/{key}", configApi.deleteField());
          javalinConfig.routes.delete("/api/v1/config/{name}", configApi.deleteConfig());
        });
  }

  private static String requestIdFromHeader(String requestIdHeader) {
    if (requestIdHeader == null || requestIdHeader.isBlank()) {
      return UUID.randomUUID().toString();
    }
    return requestIdHeader;
  }

  private static String requestId(io.javalin.http.Context ctx) {
    return Objects.toString(ctx.attribute(REQUEST_ID_CONTEXT_KEY), "unknown");
  }
}
