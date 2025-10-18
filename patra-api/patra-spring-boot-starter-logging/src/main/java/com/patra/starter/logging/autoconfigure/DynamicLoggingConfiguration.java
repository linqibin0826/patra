package com.patra.starter.logging.autoconfigure;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.ConfigurableEnvironment;

/**
 * Dynamic log level configuration via Nacos.
 *
 * <p>Implements FR-011: Dynamic log level configuration per Java package without requiring
 * application restart.
 *
 * <p>Implements SC-007: Configuration changes take effect within 60 seconds.
 *
 * <h3>How it works:</h3>
 *
 * <ol>
 *   <li>Nacos configuration files (logging-common.yml, logging-{service}.yml) define log levels
 *   <li>Spring Cloud Alibaba Nacos Config loads these files on application startup
 *   <li>Changes in Nacos console trigger {@link EnvironmentChangeEvent}
 *   <li>This listener detects logging.level.* property changes
 *   <li>Logback LoggerContext is updated dynamically (no restart)
 *   <li>New log level takes effect immediately for subsequent log statements
 * </ol>
 *
 * <h3>Configuration Example:</h3>
 *
 * <pre>{@code
 * # Nacos: logging-patra-registry.yml
 * logging:
 *   level:
 *     root: INFO
 *     com.papertrace.registry.adapter: DEBUG
 *     com.papertrace.registry.app: INFO
 * }</pre>
 *
 * <h3>Usage Scenarios (FR-011):</h3>
 *
 * <ul>
 *   <li><strong>Production Issue</strong>: Enable DEBUG logging for specific package to
 *       troubleshoot
 *   <li><strong>Performance Tuning</strong>: Reduce log level to WARN to decrease log volume
 *   <li><strong>Security Audit</strong>: Enable INFO for authentication events
 *   <li><strong>Integration Testing</strong>: Enable TRACE for external API client debugging
 * </ul>
 *
 * <h3>Performance Considerations:</h3>
 *
 * <ul>
 *   <li>Log level changes are applied immediately (no caching)
 *   <li>Minimal performance impact: O(1) lookup per logger
 *   <li>Nacos push notification ensures <60s latency (SC-007)
 *   <li>No restart required: zero downtime for configuration changes
 * </ul>
 *
 * <h3>Testing:</h3>
 *
 * <ul>
 *   <li>Unit test: {@code LogLevelConfigurationTest} (T046)
 *   <li>Integration test: {@code T047, T048} - verify <60s change propagation
 * </ul>
 *
 * @see EnvironmentChangeEvent
 * @since 0.1.0 (Phase 4 - User Story 2)
 */
@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.context.environment.EnvironmentChangeEvent")
public class DynamicLoggingConfiguration implements ApplicationListener<EnvironmentChangeEvent> {

  private static final org.slf4j.Logger log =
      LoggerFactory.getLogger(DynamicLoggingConfiguration.class);

  private static final String LOGGING_LEVEL_PREFIX = "logging.level.";

  private final ConfigurableEnvironment environment;

  public DynamicLoggingConfiguration(ConfigurableEnvironment environment) {
    this.environment = environment;
    log.info(
        "Initialized DynamicLoggingConfiguration - listening for Nacos log level changes (FR-011,"
            + " SC-007)");
  }

  /**
   * Handles environment change events from Nacos config refresh.
   *
   * <p>Detects changes to logging.level.* properties and updates Logback logger levels dynamically.
   *
   * <p><strong>Performance:</strong> Executes synchronously on config refresh thread. Nacos refresh
   * is infrequent (user-triggered), so no async handling needed.
   *
   * @param event Environment change event containing modified property keys
   */
  @Override
  public void onApplicationEvent(EnvironmentChangeEvent event) {
    log.debug("Received EnvironmentChangeEvent with {} changed keys", event.getKeys().size());

    // Filter keys related to log levels
    boolean hasLoggingChanges =
        event.getKeys().stream().anyMatch(key -> key.startsWith(LOGGING_LEVEL_PREFIX));

    if (!hasLoggingChanges) {
      log.trace("No logging.level.* changes detected, skipping log level update");
      return;
    }

    log.info(
        "Detected logging.level changes in Nacos config, applying dynamic log level updates"
            + " (FR-011)");

    // Refresh all logging.level.* properties from environment
    refreshLogLevels();
  }

  /**
   * Refreshes Logback logger levels from Spring Environment properties.
   *
   * <p>Reads all logging.level.* properties and updates corresponding Logback loggers.
   *
   * <p><strong>Implementation Notes:</strong>
   *
   * <ul>
   *   <li>Uses Spring Boot's {@link Binder} to extract logging.level map
   *   <li>Obtains Logback {@link LoggerContext} via SLF4J factory cast
   *   <li>Updates logger levels via {@link Logger#setLevel(Level)}
   *   <li>Handles special "root" logger name for root logger configuration
   * </ul>
   *
   * <p><strong>Error Handling:</strong>
   *
   * <ul>
   *   <li>Invalid log level strings → logged as WARN, skipped (no crash)
   *   <li>Missing logger → created on-demand by Logback (standard behavior)
   *   <li>LoggerContext cast failure → logged as ERROR (should never happen with Logback)
   * </ul>
   */
  private void refreshLogLevels() {
    try {
      // Extract logging.level.* map from environment using Binder
      Map<String, String> logLevels =
          Binder.get(environment)
              .bind(
                  LOGGING_LEVEL_PREFIX.substring(0, LOGGING_LEVEL_PREFIX.length() - 1),
                  org.springframework.boot.context.properties.bind.Bindable.mapOf(
                      String.class, String.class))
              .orElse(Map.of());

      if (logLevels.isEmpty()) {
        log.warn("No logging.level.* properties found in environment after config refresh");
        return;
      }

      log.debug("Found {} logger configurations to apply", logLevels.size());

      // Get Logback LoggerContext
      LoggerContext loggerContext = getLoggerContext();

      // Apply each log level configuration
      int successCount = 0;
      int failureCount = 0;

      for (Map.Entry<String, String> entry : logLevels.entrySet()) {
        String loggerName = entry.getKey();
        String levelStr = entry.getValue();

        try {
          Level level = Level.toLevel(levelStr, null);
          if (level == null) {
            log.warn(
                "Invalid log level '{}' for logger '{}', skipping (valid: ERROR, WARN, INFO,"
                    + " DEBUG, TRACE)",
                levelStr,
                loggerName);
            failureCount++;
            continue;
          }

          // Handle root logger (special case: "level.root" → root logger)
          Logger logger =
              "root".equalsIgnoreCase(loggerName)
                  ? loggerContext.getLogger(Logger.ROOT_LOGGER_NAME)
                  : loggerContext.getLogger(loggerName);

          // Update log level
          logger.setLevel(level);
          successCount++;

          log.info(
              "Updated logger '{}' to level {} (dynamic config from Nacos)", loggerName, level);

        } catch (Exception e) {
          log.error(
              "Failed to update log level for logger '{}' to '{}': {}",
              loggerName,
              levelStr,
              e.getMessage(),
              e);
          failureCount++;
        }
      }

      log.info(
          "Dynamic log level refresh completed: {} successful, {} failed, {} total (SC-007:"
              + " changes effective within 60s)",
          successCount,
          failureCount,
          logLevels.size());

    } catch (Exception e) {
      log.error("Failed to refresh log levels from environment: {}", e.getMessage(), e);
    }
  }

  /**
   * Obtains the Logback LoggerContext.
   *
   * <p>Casts SLF4J's LoggerFactory to Logback's LoggerContext. This is safe because we explicitly
   * depend on logback-classic in pom.xml.
   *
   * @return Logback logger context
   * @throws ClassCastException if SLF4J is not bound to Logback (should never happen)
   */
  private LoggerContext getLoggerContext() {
    org.slf4j.ILoggerFactory factory = LoggerFactory.getILoggerFactory();
    if (!(factory instanceof LoggerContext)) {
      throw new IllegalStateException(
          "LoggerFactory is not a Logback LoggerContext (found: "
              + factory.getClass().getName()
              + "). Dynamic log level configuration requires Logback.");
    }
    return (LoggerContext) factory;
  }
}
