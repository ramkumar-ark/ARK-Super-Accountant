package com.arktech.superaccountant.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Provides a PostgreSQL datasource for integration tests.
 *
 * <p>When {@code SA_TEST_DB_URL} is set the tests run against that scratch database (Docker is
 * not available in every environment). Otherwise a Testcontainers PostgreSQL is started lazily,
 * preserving the previous CI behaviour.
 */
public final class TestDatabase {

    private static final String DEFAULT_USERNAME = "postgres";
    private static final String DEFAULT_PASSWORD = "postgres";

    private static PostgreSQLContainer<?> container;

    private TestDatabase() {
    }

    public static synchronized void register(DynamicPropertyRegistry registry) {
        String url = System.getenv("SA_TEST_DB_URL");
        if (url != null && !url.isBlank()) {
            String username = envOrDefault("SA_TEST_DB_USERNAME", DEFAULT_USERNAME);
            String password = envOrDefault("SA_TEST_DB_PASSWORD", DEFAULT_PASSWORD);
            registry.add("spring.datasource.url", () -> url);
            registry.add("spring.datasource.username", () -> username);
            registry.add("spring.datasource.password", () -> password);
            registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
            return;
        }

        if (container == null) {
            container = new PostgreSQLContainer<>("postgres:16-alpine");
            container.start();
        }
        registry.add("spring.datasource.url", container::getJdbcUrl);
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
    }

    private static String envOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
