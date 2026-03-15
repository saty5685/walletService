package com.deezyWallet.walletService.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers configuration — imported by ALL integration test classes.
 *
 * WHY a @TestConfiguration instead of @Container on each test class?
 *
 * The default Testcontainers lifecycle is one container per test CLASS.
 * For a test suite with 3 @DataJpaTest classes that all need MySQL,
 * that means 3 container start/stop cycles — ~90 seconds of overhead.
 *
 * With @TestConfiguration + @ServiceConnection, Spring Boot 3.1+ manages
 * the container lifecycle at the ApplicationContext level. If two test
 * classes share the same ApplicationContext (same @SpringBootTest config),
 * they share the same container. One start, many tests.
 *
 * WHY @ServiceConnection?
 *   Spring Boot 3.1+ introduced @ServiceConnection. When placed on a
 *   @Bean that returns a Container, Boot automatically configures
 *   spring.datasource.url/username/password from the running container.
 *   No need to manually call container.getJdbcUrl() in @DynamicPropertySource.
 *   Cleaner, less boilerplate, same result.
 *
 * MySQL version pinned to 8.0.33 — same as production.
 * WHY pin a specific patch version?
 *   "mysql:8.0" floating tag could resolve to a different patch on different
 *   CI runners. A behaviour difference between 8.0.32 and 8.0.33 would cause
 *   flaky tests that only appear on some machines.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestContainersConfig {

	@Bean
	@ServiceConnection
	MySQLContainer<?> mySQLContainer() {
		return new MySQLContainer<>(DockerImageName.parse("mysql:8.0.33"))
				.withDatabaseName("wallet_db_test")
				.withUsername("wallet_test_user")
				.withPassword("wallet_test_pass")
				.withReuse(true);
		// withReuse(true) — Testcontainers reuses a running container
		// across test runs in the same JVM session (e.g. when running
		// tests repeatedly during development without restarting the IDE).
		// Requires ~/.testcontainers.properties: testcontainers.reuse.enable=true
		// Safe to omit in CI — containers always start fresh there.
	}
}
