package com.patra.registry.it;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public abstract class BaseRegistryIntegrationTest {
  private static final SharedMySQLContainer mysql = SharedMySQLContainer.getInstance();

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

  private static String appendParams(String url, String params) {
    if (url.contains("?")) {
      return url + "&" + params;
    }
    return url + "?" + params;
  }
}
