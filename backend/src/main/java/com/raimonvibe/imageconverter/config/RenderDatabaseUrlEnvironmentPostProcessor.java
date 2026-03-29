package com.raimonvibe.imageconverter.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

/**
 * Render, Railway, and Heroku expose {@code DATABASE_URL} as {@code postgres://} or
 * {@code postgresql://}. Spring JDBC expects {@code jdbc:postgresql://}. Without this,
 * the DataSource fails, JPA never starts, and beans like {@code UserRepository} fail with
 * {@code jpaSharedEM_entityManagerFactory} resolution errors.
 */
public class RenderDatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final String SOURCE_NAME = "renderPostgresUrlToJdbc";

  @Override
  public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
    String raw = environment.getProperty("DATABASE_URL");
    if (raw == null || raw.isBlank()) {
      return;
    }
    raw = raw.trim();
    if (raw.startsWith("jdbc:")) {
      return;
    }
    if (!raw.startsWith("postgresql://") && !raw.startsWith("postgres://")) {
      return;
    }

    String normalized = raw.startsWith("postgres://")
        ? "postgresql://" + raw.substring("postgres://".length())
        : raw;

    URI uri;
    try {
      uri = URI.create(normalized);
    } catch (IllegalArgumentException e) {
      return;
    }

    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      return;
    }

    int port = uri.getPort();
    if (port < 0) {
      port = 5432;
    }

    String path = uri.getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) {
      return;
    }
    String database = path.startsWith("/") ? path.substring(1) : path;

    StringBuilder jdbc = new StringBuilder();
    jdbc.append("jdbc:postgresql://").append(host).append(":").append(port).append("/").append(database);
    String query = uri.getRawQuery();
    if (query != null && !query.isBlank()) {
      jdbc.append("?").append(query);
    } else {
      jdbc.append("?sslmode=require");
    }

    Map<String, Object> props = new LinkedHashMap<>();
    props.put("spring.datasource.url", jdbc.toString());

    String userInfo = uri.getUserInfo();
    if (userInfo != null && !userInfo.isBlank()) {
      int colon = userInfo.indexOf(':');
      if (colon >= 0) {
        props.put(
            "spring.datasource.username",
            URLDecoder.decode(userInfo.substring(0, colon), StandardCharsets.UTF_8));
        props.put(
            "spring.datasource.password",
            URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8));
      } else {
        props.put(
            "spring.datasource.username",
            URLDecoder.decode(userInfo, StandardCharsets.UTF_8));
      }
    }

    environment.getPropertySources().addFirst(new MapPropertySource(SOURCE_NAME, props));
  }
}
