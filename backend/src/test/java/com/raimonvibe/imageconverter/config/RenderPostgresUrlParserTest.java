package com.raimonvibe.imageconverter.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RenderPostgresUrlParserTest {

  @Test
  void parsesPostgresUrlWithCredentials() {
    String raw = "postgresql://myuser:mypass@dpg-test.frankfurt-postgres.render.com:5432/mydb";
    assertTrue(RenderPostgresUrlParser.isLibpqUrl(raw));
    RenderPostgresUrlParser.Result r = RenderPostgresUrlParser.parse(raw);
    assertEquals(
        "jdbc:postgresql://dpg-test.frankfurt-postgres.render.com:5432/mydb?sslmode=require",
        r.jdbcUrl());
    assertEquals("myuser", r.username());
    assertEquals("mypass", r.password());
  }

  @Test
  void jdbcUrlIsNotLibpq() {
    assertFalse(RenderPostgresUrlParser.isLibpqUrl("jdbc:postgresql://localhost:5432/x"));
  }
}
