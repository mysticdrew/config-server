package com.webapp;

import com.webapp.config.AppConfig;
import com.webapp.http.WebServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class App {
  private static final Logger logger = LoggerFactory.getLogger(App.class);

  public static void main(String[] args) {
    AppConfig config = AppConfig.fromEnvironment();
    logger.info(
        "Starting webapp on port={} env={} configDir={}",
        config.port(),
        config.environment(),
        config.configDir());
    WebServer.create(config).start(config.port());
  }
}
