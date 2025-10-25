package com.patra.registry.it;

import org.testcontainers.containers.MySQLContainer;

/**
 * Singleton MySQL container shared across all integration tests to avoid per-class lifecycle
 * issues. The container starts once and remains up for the JVM lifetime; Testcontainers Ryuk will
 * clean it up when the JVM terminates.
 */
final class SharedMySQLContainer extends MySQLContainer<SharedMySQLContainer> {

  private static final String IMAGE = "mysql:8.0.36";
  private static final SharedMySQLContainer INSTANCE =
      new SharedMySQLContainer()
          .withDatabaseName("patra_registry")
          .withUsername("root")
          .withPassword("rootpw");

  private SharedMySQLContainer() {
    super(IMAGE);
  }

  /**
   * Returns the singleton MySQL container instance.
   *
   * @return the shared MySQL container
   */
  static SharedMySQLContainer getInstance() {
    return INSTANCE;
  }

  static {
    INSTANCE.start();
  }
}
