package com.raimonvibe.imageconverter.config;

import javax.sql.DataSource;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

import com.zaxxer.hikari.HikariDataSource;

/**
 * In production, Render sets {@code DATABASE_URL} as {@code postgresql://...}, which is not a valid
 * JDBC URL for Hikari. Auto-configuration would leave JPA without a working
 * {@code EntityManagerFactory}. This bean runs only with profile {@code prod} and normalizes the URL.
 *
 * <p>Override in Render (optional): set {@code SPRING_DATASOURCE_URL} to a full {@code jdbc:postgresql://...}
 * URL and keep {@code spring.datasource.username} / {@code password} as usual.
 */
@Configuration
@Profile("prod")
public class RenderProductionDataSourceConfiguration {

  @Bean
  @Primary
  @ConfigurationProperties("spring.datasource.hikari")
  public DataSource dataSource(Environment env) {
    String explicitJdbc = env.getProperty("SPRING_DATASOURCE_URL");
    if (explicitJdbc != null && explicitJdbc.startsWith("jdbc:")) {
      return hikari(env, explicitJdbc, null, null);
    }

    String databaseUrl = env.getProperty("DATABASE_URL");
    if (RenderPostgresUrlParser.isLibpqUrl(databaseUrl)) {
      try {
        RenderPostgresUrlParser.Result r = RenderPostgresUrlParser.parse(databaseUrl);
        return hikari(env, r.jdbcUrl(), r.username(), r.password());
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("Invalid DATABASE_URL", e);
      }
    }

    String springUrl = env.getProperty("spring.datasource.url");
    if (RenderPostgresUrlParser.isLibpqUrl(springUrl)) {
      try {
        RenderPostgresUrlParser.Result r = RenderPostgresUrlParser.parse(springUrl);
        return hikari(env, r.jdbcUrl(), r.username(), r.password());
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException("Invalid spring.datasource.url", e);
      }
    }

    if (springUrl == null || springUrl.isBlank()) {
      throw new IllegalStateException(
          "Production database not configured: set DATABASE_URL (from Render Postgres) or "
              + "SPRING_DATASOURCE_URL=jdbc:postgresql://host:5432/db?sslmode=require");
    }
    if (!springUrl.startsWith("jdbc:")) {
      throw new IllegalStateException(
          "spring.datasource.url must start with jdbc: or use a Render-style DATABASE_URL=postgresql://...");
    }
    return hikari(env, springUrl, null, null);
  }

  private static HikariDataSource hikari(
      Environment env, String jdbcUrl, String parsedUser, String parsedPass) {
    HikariDataSource ds = new HikariDataSource();
    ds.setJdbcUrl(jdbcUrl);
    if (parsedUser != null) {
      ds.setUsername(parsedUser);
    } else {
      ds.setUsername(env.getProperty("spring.datasource.username", "postgres"));
    }
    if (parsedPass != null) {
      ds.setPassword(parsedPass);
    } else {
      ds.setPassword(env.getProperty("spring.datasource.password", ""));
    }
    return ds;
  }
}
