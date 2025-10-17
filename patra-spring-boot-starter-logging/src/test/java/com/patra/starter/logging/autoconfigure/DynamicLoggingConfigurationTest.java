package com.patra.starter.logging.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

/**
 * Unit tests for {@link DynamicLoggingConfiguration}.
 *
 * <p>Verifies FR-011: Dynamic log level configuration per Java package without requiring
 * application restart.
 *
 * <p>Verifies SC-007: Configuration changes take effect within 60 seconds (simulated via immediate
 * application of environment change events).
 *
 * <p>Test Strategy:
 *
 * <ul>
 *   <li>Unit tests: Verify log level changes applied correctly to Logback LoggerContext
 *   <li>Edge cases: Invalid log levels, missing properties, root logger configuration
 *   <li>Integration test (T047, T048): Manual testing in patra-registry with actual Nacos
 * </ul>
 *
 * @see DynamicLoggingConfiguration
 * @since 0.1.0 (Phase 4 - User Story 2)
 */
@DisplayName("DynamicLoggingConfiguration Tests (FR-011, SC-007)")
class DynamicLoggingConfigurationTest {

  private ConfigurableEnvironment environment;
  private DynamicLoggingConfiguration configuration;
  private LoggerContext loggerContext;

  @BeforeEach
  void setUp() {
    // Initialize Spring Environment with logging properties
    environment = new StandardEnvironment();

    // Initialize DynamicLoggingConfiguration
    configuration = new DynamicLoggingConfiguration(environment);

    // Get Logback LoggerContext
    loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();

    // Reset all loggers to default level (INFO)
    loggerContext
        .getLoggerList()
        .forEach(
            logger -> {
              if (!Logger.ROOT_LOGGER_NAME.equals(logger.getName())) {
                logger.setLevel(null); // Inherit from parent
              }
            });
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
  }

  @AfterEach
  void tearDown() {
    // Clean up: reset loggers to avoid affecting other tests
    loggerContext
        .getLoggerList()
        .forEach(
            logger -> {
              if (!Logger.ROOT_LOGGER_NAME.equals(logger.getName())) {
                logger.setLevel(null);
              }
            });
    loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).setLevel(Level.INFO);
  }

  @Test
  @DisplayName("Should update root logger level from Nacos config (FR-011)")
  void shouldUpdateRootLoggerLevel() {
    // Given: Nacos config with root logger level = DEBUG
    addLoggingProperty("logging.level.root", "DEBUG");

    // When: EnvironmentChangeEvent is fired (simulating Nacos config refresh)
    fireEnvironmentChangeEvent("logging.level.root");

    // Then: Root logger level should be updated to DEBUG
    Logger rootLogger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    assertThat(rootLogger.getLevel()).isEqualTo(Level.DEBUG);
  }

  @Test
  @DisplayName("Should update package-specific logger level from Nacos config (FR-011)")
  void shouldUpdatePackageLoggerLevel() {
    // Given: Nacos config with package-specific level
    addLoggingProperty("logging.level.com.patra.registry.adapter", "DEBUG");

    // When: EnvironmentChangeEvent is fired
    fireEnvironmentChangeEvent("logging.level.com.patra.registry.adapter");

    // Then: Package logger level should be updated to DEBUG
    Logger logger = loggerContext.getLogger("com.patra.registry.adapter");
    assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
  }

  @Test
  @DisplayName("Should update multiple logger levels from Nacos config (FR-011)")
  void shouldUpdateMultipleLoggerLevels() {
    // Given: Nacos config with multiple logger levels
    addLoggingProperty("logging.level.root", "WARN");
    addLoggingProperty("logging.level.com.patra.registry.adapter", "DEBUG");
    addLoggingProperty("logging.level.com.patra.registry.app", "INFO");
    addLoggingProperty("logging.level.com.patra.registry.infra", "TRACE");

    // When: EnvironmentChangeEvent is fired for all keys
    fireEnvironmentChangeEvent(
        "logging.level.root",
        "logging.level.com.patra.registry.adapter",
        "logging.level.com.patra.registry.app",
        "logging.level.com.patra.registry.infra");

    // Then: All logger levels should be updated
    assertThat(loggerContext.getLogger(Logger.ROOT_LOGGER_NAME).getLevel()).isEqualTo(Level.WARN);
    assertThat(loggerContext.getLogger("com.patra.registry.adapter").getLevel())
        .isEqualTo(Level.DEBUG);
    assertThat(loggerContext.getLogger("com.patra.registry.app").getLevel()).isEqualTo(Level.INFO);
    assertThat(loggerContext.getLogger("com.patra.registry.infra").getLevel())
        .isEqualTo(Level.TRACE);
  }

  @Test
  @DisplayName("Should handle invalid log level gracefully (WARN logged, no crash)")
  void shouldHandleInvalidLogLevelGracefully() {
    // Given: Nacos config with invalid log level
    addLoggingProperty("logging.level.com.patra.registry.adapter", "INVALID_LEVEL");

    // When: EnvironmentChangeEvent is fired
    fireEnvironmentChangeEvent("logging.level.com.patra.registry.adapter");

    // Then: Logger level should remain unchanged (null = inherit from parent)
    Logger logger = loggerContext.getLogger("com.patra.registry.adapter");
    assertThat(logger.getLevel()).isNull(); // Not updated due to invalid level
  }

  @Test
  @DisplayName("Should ignore non-logging property changes")
  void shouldIgnoreNonLoggingPropertyChanges() {
    // Given: Initial logger level = INFO
    Logger logger = loggerContext.getLogger("com.patra.registry.adapter");
    logger.setLevel(Level.INFO);

    // When: EnvironmentChangeEvent with non-logging keys
    fireEnvironmentChangeEvent("server.port", "spring.datasource.url");

    // Then: Logger level should remain unchanged
    assertThat(logger.getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  @DisplayName("Should support all standard log levels (ERROR, WARN, INFO, DEBUG, TRACE)")
  void shouldSupportAllStandardLogLevels() {
    // Given: Nacos config with each standard log level
    addLoggingProperty("logging.level.test.error", "ERROR");
    addLoggingProperty("logging.level.test.warn", "WARN");
    addLoggingProperty("logging.level.test.info", "INFO");
    addLoggingProperty("logging.level.test.debug", "DEBUG");
    addLoggingProperty("logging.level.test.trace", "TRACE");

    // When: EnvironmentChangeEvent is fired
    fireEnvironmentChangeEvent(
        "logging.level.test.error",
        "logging.level.test.warn",
        "logging.level.test.info",
        "logging.level.test.debug",
        "logging.level.test.trace");

    // Then: All loggers should have correct levels
    assertThat(loggerContext.getLogger("test.error").getLevel()).isEqualTo(Level.ERROR);
    assertThat(loggerContext.getLogger("test.warn").getLevel()).isEqualTo(Level.WARN);
    assertThat(loggerContext.getLogger("test.info").getLevel()).isEqualTo(Level.INFO);
    assertThat(loggerContext.getLogger("test.debug").getLevel()).isEqualTo(Level.DEBUG);
    assertThat(loggerContext.getLogger("test.trace").getLevel()).isEqualTo(Level.TRACE);
  }

  @Test
  @DisplayName("Should update logger level dynamically (simulate runtime config change)")
  void shouldUpdateLoggerLevelDynamically() {
    // Given: Initial logger level = INFO
    addLoggingProperty("logging.level.com.patra.registry.adapter", "INFO");
    fireEnvironmentChangeEvent("logging.level.com.patra.registry.adapter");

    Logger logger = loggerContext.getLogger("com.patra.registry.adapter");
    assertThat(logger.getLevel()).isEqualTo(Level.INFO);

    // When: Nacos config updated to DEBUG (simulating runtime change)
    addLoggingProperty("logging.level.com.patra.registry.adapter", "DEBUG");
    fireEnvironmentChangeEvent("logging.level.com.patra.registry.adapter");

    // Then: Logger level should be updated to DEBUG immediately (SC-007: <60s)
    assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);

    // When: Nacos config updated back to INFO
    addLoggingProperty("logging.level.com.patra.registry.adapter", "INFO");
    fireEnvironmentChangeEvent("logging.level.com.patra.registry.adapter");

    // Then: Logger level should revert to INFO
    assertThat(logger.getLevel()).isEqualTo(Level.INFO);
  }

  @Test
  @DisplayName("Should handle case-insensitive log level values")
  void shouldHandleCaseInsensitiveLogLevels() {
    // Given: Nacos config with lowercase log level
    addLoggingProperty("logging.level.com.patra.test", "debug");

    // When: EnvironmentChangeEvent is fired
    fireEnvironmentChangeEvent("logging.level.com.patra.test");

    // Then: Logger level should be set correctly (Logback is case-insensitive)
    Logger logger = loggerContext.getLogger("com.patra.test");
    assertThat(logger.getLevel()).isEqualTo(Level.DEBUG);
  }

  @Test
  @DisplayName("Should not crash when EnvironmentChangeEvent has no logging.level keys (edge case)")
  void shouldNotCrashWhenNoLoggingKeys() {
    // Given: EnvironmentChangeEvent with empty keys
    fireEnvironmentChangeEvent(); // No keys

    // Then: No exception thrown, loggers remain unchanged
    Logger logger = loggerContext.getLogger(Logger.ROOT_LOGGER_NAME);
    assertThat(logger.getLevel()).isEqualTo(Level.INFO); // Unchanged
  }

  /**
   * Helper: Adds a logging property to the Spring Environment.
   *
   * @param key Property key (e.g., "logging.level.root")
   * @param value Property value (e.g., "DEBUG")
   */
  private void addLoggingProperty(String key, String value) {
    var propertySource =
        (MapPropertySource)
            environment.getPropertySources().stream()
                .filter(ps -> ps.getName().equals("test-logging-properties"))
                .findFirst()
                .orElseGet(
                    () -> {
                      var newSource =
                          new MapPropertySource(
                              "test-logging-properties", new java.util.HashMap<>());
                      environment.getPropertySources().addFirst(newSource);
                      return newSource;
                    });

    @SuppressWarnings("unchecked")
    var source = (java.util.Map<String, Object>) propertySource.getSource();
    source.put(key, value);
  }

  /**
   * Helper: Fires an {@link EnvironmentChangeEvent} to simulate Nacos config refresh.
   *
   * @param changedKeys Property keys that changed (e.g., "logging.level.root")
   */
  private void fireEnvironmentChangeEvent(String... changedKeys) {
    var event = new EnvironmentChangeEvent(Set.of(changedKeys));
    configuration.onApplicationEvent(event);
  }
}
