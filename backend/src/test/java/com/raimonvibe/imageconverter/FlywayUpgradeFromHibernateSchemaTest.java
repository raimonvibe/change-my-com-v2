package com.raimonvibe.imageconverter;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Rehearses what happens to the existing production database the first time a deploy includes
 * Flyway.
 *
 * <p>Production's schema was never built by the migrations. Flyway had no autoconfiguration module
 * on the classpath, so Hibernate created the tables from the entities instead. Turning Flyway on
 * therefore does not meet an empty database, it meets one that already has every table and real
 * rows in them. With {@code baseline-on-migrate=true} and {@code baseline-version=0}, Flyway
 * baselines that schema and then applies V1, V2 and V3 on top of it.
 *
 * <p>PostgresMigrationTest covers the clean-database path. This one covers the upgrade path, which
 * is the one carrying risk: every migration has to be a no-op against an already-correct schema and
 * must not disturb existing rows. So this builds the schema with Hibernate exactly as production
 * got it, writes a row, runs Flyway over the top, and checks both that the migrations report
 * success and that the row is still there afterwards.
 */
@SpringBootTest
@Testcontainers
class FlywayUpgradeFromHibernateSchemaTest {

	@Container
	@SuppressWarnings("resource") // Testcontainers closes this via the JUnit extension.
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	/**
	 * Deliberately the pre-Flyway arrangement: Hibernate builds the schema and Flyway stays off, so
	 * the context starts in the state production is in today. The migration run below is then done
	 * by hand rather than by autoconfiguration, so the test controls when it happens.
	 */
	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
		registry.add("spring.flyway.enabled", () -> "false");
		registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
		registry.add("spring.jpa.properties.hibernate.dialect",
				() -> "org.hibernate.dialect.PostgreSQLDialect");
	}

	@Autowired
	private DataSource dataSource;

	@Test
	void migrationsApplyCleanlyOverAHibernateBuiltSchemaAndPreserveData() throws Exception {
		// A row that predates the migration, standing in for real production data.
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			// version is included because Hibernate creates it NOT NULL for @Version.
			statement.executeUpdate(
					"INSERT INTO app_user (email, free_used_today, paid_credits, version) "
							+ "VALUES ('existing@example.com', 3, 12, 0)");
		}

		MigrateResult result = Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load()
				.migrate();

		assertThat(result.success).as("Flyway migrate() over the existing schema").isTrue();
		assertThat(result.migrationsExecuted)
				.as("V1, V2 and V3 recorded against the baseline")
				.isEqualTo(3);

		// The migrations are all IF NOT EXISTS against tables Hibernate already made,
		// so they must record as applied while changing nothing and touching no rows.
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(
						"SELECT email, free_used_today, paid_credits FROM app_user "
								+ "WHERE email = 'existing@example.com'")) {
			assertThat(rs.next()).as("pre-existing row survived the migration").isTrue();
			assertThat(rs.getInt("free_used_today")).isEqualTo(3);
			assertThat(rs.getInt("paid_credits")).isEqualTo(12);
		}
	}

	@Test
	void migratingATwiceMigratedDatabaseChangesNothing() throws Exception {
		Flyway flyway = Flyway.configure()
				.dataSource(dataSource)
				.locations("classpath:db/migration")
				.baselineOnMigrate(true)
				.baselineVersion("0")
				.load();

		flyway.migrate();
		MigrateResult second = flyway.migrate();

		// Guards restarts and rollbacks: a second boot must not try to reapply anything.
		assertThat(second.success).isTrue();
		assertThat(second.migrationsExecuted).as("re-running an up-to-date database").isZero();
	}
}
