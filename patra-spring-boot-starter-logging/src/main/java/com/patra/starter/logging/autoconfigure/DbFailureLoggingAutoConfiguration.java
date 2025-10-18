package com.patra.starter.logging.autoconfigure;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.starter.logging.persistence.DbFailureLogger;
import com.patra.starter.logging.persistence.DbFailureLoggingInterceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for database failure logging (Phase 6 - T069-T070).
 *
 * <p>This configuration is only active when MyBatis-Plus is on the classpath.
 *
 * <p>Registers:
 *
 * <ul>
 *   <li>{@link DbFailureLogger}: Utility for structured database error logging
 *   <li>{@link DbFailureLoggingInterceptor}: MyBatis-Plus interceptor for automatic DB failure
 *       logging
 * </ul>
 *
 * <p><strong>Conditional Activation:</strong> This configuration requires {@code
 * com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor} on the classpath. If
 * MyBatis-Plus is not present, these beans will not be registered.
 *
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
@AutoConfiguration
@ConditionalOnClass(InnerInterceptor.class)
public class DbFailureLoggingAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(DbFailureLoggingAutoConfiguration.class);

  public DbFailureLoggingAutoConfiguration() {
    log.info("Initializing Database Failure Logging (Phase 6 - US4)");
  }

  /**
   * Registers the database failure logger utility.
   *
   * <p>Provides structured logging for database operation failures per FR-007.
   *
   * @param sanitizer Log sanitizer for SQL parameter sanitization
   * @return Database failure logger bean
   */
  @Bean
  @ConditionalOnMissingBean
  public DbFailureLogger dbFailureLogger(LogSanitizer sanitizer) {
    log.debug("Registering DbFailureLogger bean");
    // DbFailureLogger constructor requires Logger and LogSanitizer
    return new DbFailureLogger(LoggerFactory.getLogger(DbFailureLogger.class), sanitizer);
  }

  /**
   * Registers the MyBatis-Plus interceptor for automatic database failure logging.
   *
   * <p>Intercepts MyBatis-Plus operations and logs failures with full context.
   *
   * @param sanitizer Log sanitizer for SQL parameter sanitization
   * @return MyBatis-Plus interceptor bean
   */
  @Bean
  @ConditionalOnMissingBean
  public DbFailureLoggingInterceptor dbFailureLoggingInterceptor(LogSanitizer sanitizer) {
    log.debug("Registering DbFailureLoggingInterceptor bean");
    // DbFailureLoggingInterceptor constructor requires LogSanitizer
    return new DbFailureLoggingInterceptor(sanitizer);
  }
}
