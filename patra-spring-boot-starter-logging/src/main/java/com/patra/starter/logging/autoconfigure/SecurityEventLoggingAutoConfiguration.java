package com.patra.starter.logging.autoconfigure;

import com.patra.common.logging.sanitizer.LogSanitizer;
import com.patra.starter.logging.security.AuthenticationEventLogger;
import com.patra.starter.logging.security.SecurityEventLogger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.security.authentication.AuthenticationManager;

/**
 * Auto-configuration for security event logging (Phase 6 - T071-T072).
 *
 * <p>This configuration is only active when Spring Security is on the classpath.
 *
 * <p>Registers:
 *
 * <ul>
 *   <li>{@link SecurityEventLogger}: Utility for structured security audit logging
 *   <li>{@link AuthenticationEventLogger}: Spring Security event listener for authentication audit
 * </ul>
 *
 * <p><strong>Conditional Activation:</strong> This configuration requires {@code
 * org.springframework.security.authentication.AuthenticationManager} on the classpath. If Spring
 * Security is not present, these beans will not be registered.
 *
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
@AutoConfiguration
@ConditionalOnClass(AuthenticationManager.class)
public class SecurityEventLoggingAutoConfiguration {

  private static final Logger log =
      LoggerFactory.getLogger(SecurityEventLoggingAutoConfiguration.class);

  public SecurityEventLoggingAutoConfiguration() {
    log.info("Initializing Security Event Logging (Phase 6 - US4)");
  }

  /**
   * Registers the security event logger utility.
   *
   * <p>Provides structured logging for authentication and authorization events per FR-009.
   *
   * @param sanitizer Log sanitizer for username/credential sanitization
   * @return Security event logger bean
   */
  @Bean
  @ConditionalOnMissingBean
  public SecurityEventLogger securityEventLogger(LogSanitizer sanitizer) {
    log.debug("Registering SecurityEventLogger bean");
    // SecurityEventLogger constructor requires Logger and LogSanitizer
    return new SecurityEventLogger(LoggerFactory.getLogger(SecurityEventLogger.class), sanitizer);
  }

  /**
   * Registers the Spring Security authentication event listener.
   *
   * <p>Listens to Spring Security authentication events and logs them with full context.
   *
   * @param sanitizer Log sanitizer for username/credential sanitization
   * @return Authentication event listener bean
   */
  @Bean
  @ConditionalOnMissingBean
  public AuthenticationEventLogger authenticationEventLogger(LogSanitizer sanitizer) {
    log.debug("Registering AuthenticationEventLogger bean");
    // AuthenticationEventLogger constructor requires LogSanitizer
    return new AuthenticationEventLogger(sanitizer);
  }
}
