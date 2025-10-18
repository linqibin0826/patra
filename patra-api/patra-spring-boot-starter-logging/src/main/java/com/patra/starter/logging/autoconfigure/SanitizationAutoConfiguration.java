package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.sanitizer.DefaultLogSanitizer;
import com.patra.common.logging.sanitizer.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for log sanitization functionality.
 *
 * <p>Registers {@link LogSanitizer} bean for automatic sensitive data sanitization in logs.
 *
 * <p>Implements User Story 4 requirements (T065):
 *
 * <ul>
 *   <li>FR-008: Automatic sensitive data sanitization
 *   <li>SC-006: Zero instances of sensitive data in logs
 * </ul>
 *
 * <h3>Functionality:</h3>
 *
 * <ul>
 *   <li>Registers {@link DefaultLogSanitizer} as the default sanitizer implementation
 *   <li>Allows custom sanitizer by using {@code @Primary} or excluding this auto-configuration
 *   <li>Sanitizer is available for injection in all application components
 * </ul>
 *
 * <h3>Usage in Application Code:</h3>
 *
 * <pre>{@code
 * @Service
 * public class MyService {
 *
 *     private static final Logger log = LoggerFactory.getLogger(MyService.class);
 *
 *     @Autowired
 *     private LogSanitizer sanitizer;
 *
 *     public void processUserData(UserDto user) {
 *         // Sanitize sensitive data before logging
 *         log.info("Processing user: {}", sanitizer.sanitizeObject(user));
 *
 *         // Or sanitize JSON string
 *         String jsonPayload = "...";
 *         log.debug("API request: {}", sanitizer.sanitizeJson(jsonPayload));
 *     }
 * }
 * }</pre>
 *
 * <h3>Custom Sanitizer:</h3>
 *
 * To replace the default sanitizer, define a {@code @Primary} bean:
 *
 * <pre>{@code
 * @Configuration
 * public class CustomSanitizationConfig {
 *
 *     @Bean
 *     @Primary
 *     public LogSanitizer customSanitizer() {
 *         return new MyCustomSanitizer();
 *     }
 * }
 * }</pre>
 *
 * <h3>Disabling Sanitization:</h3>
 *
 * To disable auto-configuration (not recommended for production):
 *
 * <pre>{@code
 * @SpringBootApplication(exclude = SanitizationAutoConfiguration.class)
 * public class MyApplication {
 *     // ...
 * }
 * }</pre>
 *
 * @see LogSanitizer
 * @see DefaultLogSanitizer
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
@AutoConfiguration
public class SanitizationAutoConfiguration {

  private static final Logger log = LoggerFactory.getLogger(SanitizationAutoConfiguration.class);

  /**
   * Registers the default log sanitizer bean.
   *
   * <p>This bean is available for injection in all application components. It provides methods for
   * sanitizing:
   *
   * <ul>
   *   <li>Plain text messages ({@code sanitize(String)})
   *   <li>JSON strings ({@code sanitizeJson(String)})
   *   <li>Java objects ({@code sanitizeObject(Object)})
   * </ul>
   *
   * <p>The sanitizer is thread-safe and optimized for high-throughput logging scenarios.
   *
   * @return DefaultLogSanitizer instance configured with hardcoded patterns
   */
  @Bean
  @ConditionalOnMissingBean(LogSanitizer.class)
  public LogSanitizer logSanitizer() {
    log.info("Initializing Log Sanitization (Phase 6 - US4: FR-008, SC-006)");
    return new DefaultLogSanitizer();
  }
}
