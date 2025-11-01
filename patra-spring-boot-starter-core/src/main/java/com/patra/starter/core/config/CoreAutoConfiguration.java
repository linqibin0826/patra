package com.patra.starter.core.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for core infrastructure beans.
 *
 * <p>Provides fundamental beans that are shared across the entire application:
 *
 * <ul>
 *   <li>{@link Clock} - Centralized time source for timestamp generation
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><strong>Centralization</strong>: Single source of truth for infrastructure beans
 *   <li><strong>Overridability</strong>: Uses {@code @ConditionalOnMissingBean} to allow custom
 *       implementations
 *   <li><strong>Testability</strong>: Beans can be replaced in tests (e.g., Clock.fixed() for
 *       deterministic testing)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@AutoConfiguration
public class CoreAutoConfiguration {

  /**
   * Provides a system UTC Clock bean for timestamp generation.
   *
   * <p>Benefits:
   *
   * <ul>
   *   <li><strong>Consistency</strong>: All timestamps across the application use the same time
   *       zone (UTC)
   *   <li><strong>Testability</strong>: Can be mocked in tests with {@code Clock.fixed()} for
   *       deterministic testing
   *   <li><strong>Centralization</strong>: Single time source eliminates duplicate Clock bean
   *       definitions
   * </ul>
   *
   * <p>Usage example:
   *
   * <pre>{@code
   * @Service
   * public class MyService {
   *   private final Clock clock;
   *
   *   public MyService(Clock clock) {
   *     this.clock = clock;
   *   }
   *
   *   public void doSomething() {
   *     Instant now = Instant.now(clock);
   *   }
   * }
   * }</pre>
   *
   * @return Clock instance using system default zone (UTC)
   */
  @Bean
  @ConditionalOnMissingBean
  public Clock clock() {
    return Clock.systemUTC();
  }
}
