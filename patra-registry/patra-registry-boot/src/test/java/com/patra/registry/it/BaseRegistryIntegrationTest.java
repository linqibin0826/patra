package com.patra.registry.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base class for patra-registry integration tests.
 *
 * <p>Provides shared configuration for all integration tests, including:
 *
 * <ul>
 *   <li>Testcontainers MySQL database setup via {@link SharedMySQLContainer}
 *   <li>MockMvc configuration for HTTP endpoint testing
 *   <li>Nacos config/discovery disabled for isolated testing
 * </ul>
 *
 * <p>All integration tests should extend this class to inherit the common test infrastructure.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseRegistryIntegrationTest {
  private static final SharedMySQLContainer mysql = SharedMySQLContainer.getInstance();

  /**
   * Configures Spring properties dynamically from the shared MySQL container.
   *
   * <p>Registers datasource, Flyway, and Nacos properties for the test context.
   *
   * @param registry the dynamic property registry
   */
  @DynamicPropertySource
  static void registerDataSource(DynamicPropertyRegistry registry) {
    registry.add(
        "spring.datasource.url",
        () -> appendParams(mysql.getJdbcUrl(), "useSSL=false&allowPublicKeyRetrieval=true"));
    registry.add("spring.datasource.username", mysql::getUsername);
    registry.add("spring.datasource.password", mysql::getPassword);
    // Hikari tuning to accommodate container startup time
    registry.add("spring.datasource.hikari.connection-timeout", () -> "60000");
    // Align Flyway with the same privileged account to run DDL/DML seeds
    registry.add("spring.flyway.user", mysql::getUsername);
    registry.add("spring.flyway.password", mysql::getPassword);
    // Disable externalized config discovery in tests
    registry.add("spring.cloud.nacos.config.enabled", () -> "false");
    registry.add("spring.cloud.nacos.discovery.enabled", () -> "false");
  }

  /**
   * Appends query parameters to a JDBC URL.
   *
   * @param url the base JDBC URL
   * @param params the query parameters to append (e.g.,
   *     "useSSL=false&allowPublicKeyRetrieval=true")
   * @return the URL with appended parameters
   */
  private static String appendParams(String url, String params) {
    if (url.contains("?")) {
      return url + "&" + params;
    }
    return url + "?" + params;
  }
}
