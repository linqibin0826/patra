package com.patra.starter.logging.security;

import com.patra.common.logging.sanitizer.LogSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.security.authentication.event.AbstractAuthenticationFailureEvent;
import org.springframework.security.authentication.event.AuthenticationSuccessEvent;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
import org.springframework.security.authorization.event.AuthorizationDeniedEvent;
import org.springframework.security.core.Authentication;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Spring Security event listener that automatically logs authentication and authorization events.
 *
 * <p>Implements FR-009 (Authentication & Authorization Logging) and SC-008 (100% Audit Logging) for
 * Spring Security.
 *
 * <h3>Functionality:</h3>
 *
 * <ul>
 *   <li>Listens to Spring Security authentication events
 *   <li>Logs successful logins, failed logins, logouts, and access denied events
 *   <li>Includes IP addresses for security audit trails
 *   <li>Automatically sanitizes sensitive data (email addresses in usernames)
 *   <li>Uses {@link SecurityEventLogger} for consistent log formatting
 * </ul>
 *
 * <h3>Configuration:</h3>
 *
 * This class is automatically configured when the logging starter is added to a Spring Security
 * application:
 *
 * <pre>{@code
 * @Configuration
 * @EnableWebSecurity
 * public class SecurityConfig {
 *
 *     @Bean
 *     public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
 *         // Spring Security configuration
 *         return http.build();
 *     }
 *
 *     // AuthenticationEventLogger is auto-configured by logging starter
 * }
 * }</pre>
 *
 * <h3>Events Logged:</h3>
 *
 * <ul>
 *   <li><strong>AuthenticationSuccessEvent</strong>: User login success (INFO)
 *   <li><strong>InteractiveAuthenticationSuccessEvent</strong>: Interactive login success (INFO)
 *   <li><strong>AbstractAuthenticationFailureEvent</strong>: Login failure (WARN)
 *   <li><strong>LogoutSuccessEvent</strong>: User logout (INFO)
 *   <li><strong>AuthorizationDeniedEvent</strong>: Access denied (WARN)
 * </ul>
 *
 * <h3>Log Output Example:</h3>
 *
 * <pre>
 * INFO  Security LOGIN SUCCESS [user=john.doe] ip=192.168.1.100
 * WARN  Security LOGIN FAILURE [user=john.doe] ip=192.168.1.100 reason=Bad credentials
 * WARN  Security ACCESS DENIED [user=john.doe] resource=/admin/users action=READ reason=Access Denied
 * INFO  Security LOGOUT [user=john.doe] ip=192.168.1.100
 * </pre>
 *
 * <h3>Security Considerations:</h3>
 *
 * <ul>
 *   <li>Never logs passwords or tokens
 *   <li>IP addresses logged for failed attempts (security monitoring)
 *   <li>Sanitizes email addresses in usernames
 *   <li>Logs at appropriate levels (INFO for success, WARN for failures)
 * </ul>
 *
 * @see SecurityEventLogger
 * @see LogSanitizer
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
public class AuthenticationEventLogger {

  private static final Logger log = LoggerFactory.getLogger(AuthenticationEventLogger.class);

  private final SecurityEventLogger securityLogger;

  /**
   * Creates the event listener with the given sanitizer.
   *
   * @param sanitizer Log sanitizer for redacting sensitive data
   */
  public AuthenticationEventLogger(LogSanitizer sanitizer) {
    Logger securityLog = LoggerFactory.getLogger("SecurityEvents");
    this.securityLogger = new SecurityEventLogger(securityLog, sanitizer);
  }

  /**
   * Logs successful authentication events (user login).
   *
   * <p>Listens to both {@link AuthenticationSuccessEvent} and {@link
   * InteractiveAuthenticationSuccessEvent}.
   *
   * @param event Authentication success event
   */
  @EventListener
  public void onAuthenticationSuccess(AuthenticationSuccessEvent event) {
    Authentication authentication = event.getAuthentication();
    String username = authentication.getName();
    String ipAddress = getClientIpAddress();

    securityLogger.logSuccessfulLogin(username, ipAddress);
  }

  /**
   * Logs interactive authentication success events (form-based login).
   *
   * @param event Interactive authentication success event
   */
  @EventListener
  public void onInteractiveAuthenticationSuccess(InteractiveAuthenticationSuccessEvent event) {
    // Already logged by AuthenticationSuccessEvent, but log at DEBUG for completeness
    Authentication authentication = event.getAuthentication();
    String username = authentication.getName();
    log.debug("Interactive authentication success for user: {}", username);
  }

  /**
   * Logs authentication failure events (failed login attempts).
   *
   * <p>Includes failure reason for security analysis.
   *
   * @param event Authentication failure event
   */
  @EventListener
  public void onAuthenticationFailure(AbstractAuthenticationFailureEvent event) {
    Authentication authentication = event.getAuthentication();
    String username = authentication != null ? authentication.getName() : "unknown";
    String ipAddress = getClientIpAddress();
    String reason = event.getException().getMessage();

    securityLogger.logFailedLogin(username, ipAddress, reason);
  }

  /**
   * Logs logout events.
   *
   * <p>Note: Spring Security does not have a built-in LogoutSuccessEvent. For logout logging, you
   * may need to implement a custom LogoutSuccessHandler:
   *
   * <pre>{@code
   * @Bean
   * public LogoutSuccessHandler logoutSuccessHandler(SecurityEventLogger logger) {
   *     return (request, response, authentication) -> {
   *         String username = authentication.getName();
   *         String ipAddress = request.getRemoteAddr();
   *         logger.logLogout(username, ipAddress);
   *         response.setStatus(HttpServletResponse.SC_OK);
   *     };
   * }
   * }</pre>
   *
   * @param event Logout success event (if custom event is published)
   */
  @EventListener
  public void onLogoutSuccess(LogoutSuccessEvent event) {
    Authentication authentication = event.getAuthentication();
    String username = authentication != null ? authentication.getName() : "unknown";
    String ipAddress = getClientIpAddress();

    securityLogger.logLogout(username, ipAddress);
  }

  /**
   * Logs authorization denied events (access control failures).
   *
   * <p>Triggered when a user attempts to access a resource they don't have permissions for.
   *
   * @param event Authorization denied event
   */
  @EventListener
  public void onAuthorizationDenied(AuthorizationDeniedEvent<?> event) {
    Authentication authentication = event.getAuthentication().get();
    String username = authentication != null ? authentication.getName() : "anonymous";

    // Extract resource and action from authorization decision
    String resource = extractResource(event);
    String action = extractAction(event);
    String reason = "Access Denied";

    securityLogger.logAccessDenied(username, resource, action, reason);
  }

  /**
   * Extracts client IP address from the current HTTP request.
   *
   * <p>Handles X-Forwarded-For header for proxied requests.
   *
   * @return Client IP address or "unknown" if not available
   */
  private String getClientIpAddress() {
    try {
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();

        // Check X-Forwarded-For header (for proxied requests)
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
          // X-Forwarded-For may contain multiple IPs, take the first one
          return xForwardedFor.split(",")[0].trim();
        }

        // Fallback to remote address
        return request.getRemoteAddr();
      }
    } catch (Exception e) {
      log.debug("Unable to extract client IP address: {}", e.getMessage());
    }

    return "unknown";
  }

  /**
   * Extracts resource identifier from authorization denied event.
   *
   * @param event Authorization denied event
   * @return Resource identifier (e.g., URL path, method name)
   */
  private String extractResource(AuthorizationDeniedEvent<?> event) {
    try {
      Object source = event.getSource();
      if (source != null) {
        return source.toString();
      }
    } catch (Exception e) {
      log.debug("Unable to extract resource from event: {}", e.getMessage());
    }
    return "unknown";
  }

  /**
   * Extracts action from authorization denied event.
   *
   * @param event Authorization denied event
   * @return Action (e.g., READ, WRITE, DELETE)
   */
  private String extractAction(AuthorizationDeniedEvent<?> event) {
    try {
      // Try to extract HTTP method from request
      ServletRequestAttributes attributes =
          (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
      if (attributes != null) {
        HttpServletRequest request = attributes.getRequest();
        return request.getMethod();
      }
    } catch (Exception e) {
      log.debug("Unable to extract action from event: {}", e.getMessage());
    }
    return "unknown";
  }
}
