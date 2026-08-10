package com.raimonvibe.imageconverter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Runs the Flyway migrations against a real PostgreSQL server.
 *
 * <p>Every other test in this suite uses H2 with {@code spring.flyway.enabled=false} and lets
 * Hibernate build the schema from the entities via {@code ddl-auto=create-drop}. That combination
 * means the files in {@code db/migration} are never executed anywhere except production: they use
 * {@code BIGSERIAL} and other PostgreSQL-only syntax, so they cannot run on H2 at all. A migration
 * that fails to apply, or that drifts from the entities, would first be discovered on deploy.
 *
 * <p>This test closes that gap by inverting the arrangement for one context. Flyway owns the schema
 * and {@code ddl-auto} is set to {@code validate}, so Hibernate asserts that the entity mappings
 * match what the migrations actually produced instead of quietly generating a schema that agrees
 * with itself. Simply reaching a test method proves both halves: the container started, the
 * migrations applied cleanly in order, and every entity validated against the result.
 *
 * <p>The image tracks {@code docker-compose.yml} so the engine under test matches local development.
 * Testcontainers needs a working Docker daemon, which GitHub's ubuntu runners provide.
 */
@SpringBootTest
@Testcontainers
class PostgresMigrationTest {

	@Container
	@SuppressWarnings("resource") // Testcontainers closes this via the JUnit extension.
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	/**
	 * Overrides the H2 settings that {@code src/test/resources/application.yml} applies to every
	 * test. {@code @DynamicPropertySource} wins over property files, so this is what redirects the
	 * context onto the container without disturbing the other tests.
	 */
	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.flyway.enabled", () -> "true");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
		registry.add("spring.jpa.properties.hibernate.dialect",
				() -> "org.hibernate.dialect.PostgreSQLDialect");
	}

	@Autowired
	private DataSource dataSource;

	@Test
	void everyMigrationAppliedSuccessfully() throws Exception {
		List<String> versions = new ArrayList<>();
		List<String> failed = new ArrayList<>();

		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT version, success FROM flyway_schema_history "
								+ "WHERE version IS NOT NULL ORDER BY installed_rank")) {
			while (rs.next()) {
				String version = rs.getString("version");
				versions.add(version);
				if (!rs.getBoolean("success")) {
					failed.add(version);
				}
			}
		}

		assertThat(failed).as("migrations recorded as failed by Flyway").isEmpty();
		// Asserted as a prefix rather than an exact list so that adding V3 does not
		// break this test, while a migration silently disappearing still does.
		assertThat(versions).as("applied migration versions").startsWith("1", "2");
	}

	@Test
	void migratedSchemaContainsTheEntityTables() throws Exception {
		List<String> tables = new ArrayList<>();

		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT table_name FROM information_schema.tables "
								+ "WHERE table_schema = 'public'")) {
			while (rs.next()) {
				tables.add(rs.getString("table_name"));
			}
		}

		assertThat(tables).contains("app_user", "ip_conversion_tracker", "webhook_events");
	}

	/**
	 * V2 adds the optimistic-locking column that guards against the concurrent credit updates
	 * covered by ConcurrencySecurityTest. Under H2 that column exists only because Hibernate
	 * generates it from the entity, so nothing currently proves the migration adds it too.
	 */
	@Test
	void v2AddedTheOptimisticLockingVersionColumn() throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT data_type FROM information_schema.columns "
								+ "WHERE table_name = 'app_user' AND column_name = 'version'")) {
			assertThat(rs.next()).as("app_user.version exists after migration").isTrue();
			assertThat(rs.getString("data_type")).isEqualTo("bigint");
		}
	}
}
