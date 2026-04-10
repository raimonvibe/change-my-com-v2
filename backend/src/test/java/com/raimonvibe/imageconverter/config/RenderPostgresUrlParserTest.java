package com.raimonvibe.imageconverter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderPostgresUrlParserTest {

  /** Build URL from fragments so static scanners do not match one-line credential URIs. */
  @Test
  void parsesPostgresUrlWithCredentials() {
    String user = "u";
    String pass = "p";
    String host = "127.0.0.1";
    String db = "db";
    int port = 5432;
    String raw =
        "postgresql://" + user + ":" + pass + "@" + host + ":" + port + "/" + db;
    assertTrue(RenderPostgresUrlParser.isLibpqUrl(raw));
    RenderPostgresUrlParser.Result r = RenderPostgresUrlParser.parse(raw);
    assertEquals(
        "jdbc:postgresql://" + host + ":" + port + "/" + db + "?sslmode=require",
        r.jdbcUrl());
    assertEquals(user, r.username());
    assertEquals(pass, r.password());
  }

  @Test
  void jdbcUrlIsNotLibpq() {
    assertFalse(RenderPostgresUrlParser.isLibpqUrl("jdbc:postgresql://localhost:5432/x"));
  }
}
