package com.webapp.api.config;

import com.webapp.api.model.config.ConfigDocumentResponse;
import com.webapp.api.model.config.ConfigFieldResponse;
import com.webapp.api.model.config.ConfigSummaryResponse;
import com.webapp.api.model.config.CreateConfigRequest;
import com.webapp.api.model.config.PutFieldRequest;
import com.webapp.configstore.ConfigDocument;
import com.webapp.configstore.ConfigField;
import com.webapp.configstore.ConfigService;
import io.javalin.http.Handler;
import java.util.List;

public class ConfigApi {
  private final ConfigService configService;

  public ConfigApi(ConfigService configService) {
    this.configService = configService;
  }

  public Handler listConfigs() {
    return ctx -> {
      List<ConfigSummaryResponse> response =
          configService.listConfigNames().stream()
              .map(
                  name ->
                      new ConfigSummaryResponse(
                          name, configService.getConfig(name).fields().size()))
              .toList();
      ctx.json(response);
    };
  }

  public Handler createConfig() {
    return ctx -> {
      CreateConfigRequest request = ctx.bodyAsClass(CreateConfigRequest.class);
      configService.createConfig(request.name());
      ctx.status(201);
    };
  }

  public Handler getConfigJson() {
    return ctx -> {
      String name = ctx.pathParam("name");
      ConfigDocument config = configService.getConfig(name);
      ctx.json(toResponse(config));
    };
  }

  public Handler getConfigProperties() {
    return ctx -> {
      String name = ctx.pathParam("name");
      String key = ctx.queryParam("key");
      String body =
          key == null ? configService.renderProperties(name) : configService.getFieldValue(name, key);
      ctx.contentType("text/plain; charset=utf-8");
      ctx.result(body);
    };
  }

  public Handler putField() {
    return ctx -> {
      String name = ctx.pathParam("name");
      String key = ctx.pathParam("key");
      PutFieldRequest request = ctx.bodyAsClass(PutFieldRequest.class);
      configService.putField(name, key, request.type(), request.value());
      ctx.status(204);
    };
  }

  public Handler deleteField() {
    return ctx -> {
      String name = ctx.pathParam("name");
      String key = ctx.pathParam("key");
      configService.deleteField(name, key);
      ctx.status(204);
    };
  }

  public Handler deleteConfig() {
    return ctx -> {
      String name = ctx.pathParam("name");
      configService.deleteConfig(name);
      ctx.status(204);
    };
  }

  private ConfigDocumentResponse toResponse(ConfigDocument config) {
    List<ConfigFieldResponse> fields = config.fields().stream().map(this::toFieldResponse).toList();
    return new ConfigDocumentResponse(config.name(), fields);
  }

  private ConfigFieldResponse toFieldResponse(ConfigField field) {
    return new ConfigFieldResponse(field.key(), field.type().name(), field.typedValue());
  }
}
