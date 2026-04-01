package com.raimonvibe.imageconverter.config;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * Render / Heroku-style {@code postgres://} URLs vs JDBC {@code jdbc:postgresql://}.
 */
public final class RenderPostgresUrlParser {

  public record Result(String jdbcUrl, String username, String password) {}

  private RenderPostgresUrlParser() {}

  public static boolean isLibpqUrl(String raw) {
    if (raw == null || raw.isBlank()) {
      return false;
    }
    String t = raw.trim();
    return !t.startsWith("jdbc:")
        && (t.startsWith("postgresql://") || t.startsWith("postgres://"));
  }

  /** @throws IllegalArgumentException if the URL cannot be parsed */
  public static Result parse(String raw) {
    String trimmed = raw.trim();
    String normalized =
        trimmed.startsWith("postgres://")
            ? "postgresql://" + trimmed.substring("postgres://".length())
            : trimmed;

    URI uri;
    try {
      uri = URI.create(normalized);
    } catch (IllegalArgumentException e) {
      // Do not rethrow with URI text — connection strings contain credentials.
      throw new IllegalArgumentException("DATABASE_URL is not a valid URI");
    }
    String host = uri.getHost();
    if (host == null || host.isBlank()) {
      throw new IllegalArgumentException("DATABASE_URL has no host");
    }
    int port = uri.getPort();
    if (port < 0) {
      port = 5432;
    }
    String path = uri.getPath();
    if (path == null || path.isEmpty() || "/".equals(path)) {
      throw new IllegalArgumentException("DATABASE_URL has no database name in path");
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

    String user = null;
    String pass = null;
    String userInfo = uri.getUserInfo();
    if (userInfo != null && !userInfo.isBlank()) {
      int colon = userInfo.indexOf(':');
      if (colon >= 0) {
        user = URLDecoder.decode(userInfo.substring(0, colon), StandardCharsets.UTF_8);
        pass = URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8);
      } else {
        user = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
      }
    }
    return new Result(jdbc.toString(), user, pass);
  }
}
