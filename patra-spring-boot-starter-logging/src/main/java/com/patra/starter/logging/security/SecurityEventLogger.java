package com.patra.starter.logging.security;

import com.patra.common.logging.sanitizer.LogSanitizer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for standardized logging of security events (authentication and authorization).
 *
 * <p>Implements FR-009 (Authentication & Authorization Logging) and SC-008 (100% Audit Logging).
 *
 * <h3>Purpose:</h3>
 *
 * Provides consistent logging format for security-critical events:
 *
 * <ul>
 *   <li>Successful logins
 *   <li>Failed login attempts (with IP address)
 *   <li>Authorization failures (access denied)
 *   <li>Logout events
 *   <li>Password changes
 *   <li>Account lockouts
 *   <li>Token validation failures
 * </ul>
 *
 * <h3>Usage Example:</h3>
 *
 * <pre>{@code
 * // In security configuration or authentication handler
 * private static final Logger log = LoggerFactory.getLogger(AuthenticationHandler.class);
 * private final SecurityEventLogger securityLogger;
 *
 * public void handleSuccessfulLogin(String username, String ipAddress) {
 *     securityLogger.logSuccessfulLogin(username, ipAddress);
 * }
 *
 * public void handleFailedLogin(String username, String ipAddress, String reason) {
 *     securityLogger.logFailedLogin(username, ipAddress, reason);
 * }
 * }</pre>
 *
 * <h3>Log Format:</h3>
 *
 * <pre>
 * INFO  Security LOGIN SUCCESS [user=john.doe] ip=192.168.1.100
 * WARN  Security LOGIN FAILURE [user=john.doe] ip=192.168.1.100 reason=Invalid password attempt=1
 * WARN  Security ACCESS DENIED [user=john.doe] resource=/admin/users action=DELETE reason=Insufficient permissions
 * INFO  Security LOGOUT [user=john.doe] ip=192.168.1.100
 * </pre>
 *
 * <h3>Sanitization:</h3>
 *
 * Automatically sanitizes:
 *
 * <ul>
 *   <li>Passwords in error messages
 *   <li>Tokens in logs
 *   <li>Email addresses in usernames (partially masked)
 * </ul>
 *
 * <h3>Security Considerations:</h3>
 *
 * <ul>
 *   <li>Never log passwords or tokens (even failed attempts)
 *   <li>Log IP addresses for failed attempts (security audit)
 *   <li>Log resource paths for access denied events (compliance)
 *   <li>Use WARN for failures, INFO for success (monitoring)
 * </ul>
 *
 * @see LogSanitizer
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
public class SecurityEventLogger {

  private static final Logger log = LoggerFactory.getLogger(SecurityEventLogger.class);

  private final Logger logger;
  private final LogSanitizer sanitizer;

  /**
   * Creates a SecurityEventLogger with the given logger and sanitizer.
   *
   * @param logger The SLF4J logger for security events
   * @param sanitizer Log sanitizer for redacting sensitive data
   */
  public SecurityEventLogger(Logger logger, LogSanitizer sanitizer) {
    this.logger = logger;
    this.sanitizer = sanitizer;
  }

  /**
   * Logs a successful login at INFO level.
   *
   * <p>Format: {@code Security LOGIN SUCCESS [user=X] ip=Y}
   *
   * @param username Username or user ID (will be sanitized if it's an email)
   * @param ipAddress Client IP address
   */
  public void logSuccessfulLogin(String username, String ipAddress) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.info("Security LOGIN SUCCESS [user={}] ip={}", sanitizedUsername, ipAddress);
  }

  /**
   * Logs a failed login at WARN level.
   *
   * <p>Format: {@code Security LOGIN FAILURE [user=X] ip=Y reason=Z attempt=N}
   *
   * @param username Username or user ID
   * @param ipAddress Client IP address
   * @param reason Failure reason (e.g., "Invalid password", "Account locked", "User not found")
   */
  public void logFailedLogin(String username, String ipAddress, String reason) {
    String sanitizedUsername = sanitizer.sanitize(username);
    String sanitizedReason = sanitizer.sanitize(reason);
    logger.warn(
        "Security LOGIN FAILURE [user={}] ip={} reason={}",
        sanitizedUsername,
        ipAddress,
        sanitizedReason);
  }

  /**
   * Logs a failed login with attempt count at WARN level.
   *
   * <p>Format: {@code Security LOGIN FAILURE [user=X] ip=Y reason=Z attempt=N}
   *
   * @param username Username or user ID
   * @param ipAddress Client IP address
   * @param reason Failure reason
   * @param attemptNumber Failed attempt number (for account lockout tracking)
   */
  public void logFailedLoginWithAttempt(
      String username, String ipAddress, String reason, int attemptNumber) {
    String sanitizedUsername = sanitizer.sanitize(username);
    String sanitizedReason = sanitizer.sanitize(reason);
    logger.warn(
        "Security LOGIN FAILURE [user={}] ip={} reason={} attempt={}",
        sanitizedUsername,
        ipAddress,
        sanitizedReason,
        attemptNumber);
  }

  /**
   * Logs an access denied event at WARN level.
   *
   * <p>Format: {@code Security ACCESS DENIED [user=X] resource=Y action=Z reason=R}
   *
   * @param username Username or user ID
   * @param resource Resource path or identifier (e.g., "/admin/users", "article:123")
   * @param action Action attempted (e.g., "READ", "DELETE", "UPDATE")
   * @param reason Denial reason (e.g., "Insufficient permissions", "Resource not found")
   */
  public void logAccessDenied(String username, String resource, String action, String reason) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.warn(
        "Security ACCESS DENIED [user={}] resource={} action={} reason={}",
        sanitizedUsername,
        resource,
        action,
        reason);
  }

  /**
   * Logs a logout event at INFO level.
   *
   * <p>Format: {@code Security LOGOUT [user=X] ip=Y}
   *
   * @param username Username or user ID
   * @param ipAddress Client IP address
   */
  public void logLogout(String username, String ipAddress) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.info("Security LOGOUT [user={}] ip={}", sanitizedUsername, ipAddress);
  }

  /**
   * Logs a password change at INFO level.
   *
   * <p>Format: {@code Security PASSWORD CHANGE [user=X] ip=Y}
   *
   * @param username Username or user ID
   * @param ipAddress Client IP address
   */
  public void logPasswordChange(String username, String ipAddress) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.info("Security PASSWORD CHANGE [user={}] ip={}", sanitizedUsername, ipAddress);
  }

  /**
   * Logs an account lockout at ERROR level.
   *
   * <p>Format: {@code Security ACCOUNT LOCKOUT [user=X] ip=Y reason=Z}
   *
   * @param username Username or user ID
   * @param ipAddress Client IP address
   * @param reason Lockout reason (e.g., "Too many failed login attempts", "Suspicious activity")
   */
  public void logAccountLockout(String username, String ipAddress, String reason) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.error(
        "Security ACCOUNT LOCKOUT [user={}] ip={} reason={}", sanitizedUsername, ipAddress, reason);
  }

  /**
   * Logs a token validation failure at WARN level.
   *
   * <p>Format: {@code Security TOKEN VALIDATION FAILURE [user=X] reason=Y}
   *
   * @param username Username or user ID (if available)
   * @param reason Failure reason (e.g., "Token expired", "Invalid signature", "Token revoked")
   */
  public void logTokenValidationFailure(String username, String reason) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.warn("Security TOKEN VALIDATION FAILURE [user={}] reason={}", sanitizedUsername, reason);
  }

  /**
   * Logs a privilege escalation attempt at ERROR level.
   *
   * <p>Format: {@code Security PRIVILEGE ESCALATION ATTEMPT [user=X] ip=Y from=A to=B}
   *
   * @param username Username or user ID
   * @param ipAddress Client IP address
   * @param currentRole Current user role
   * @param attemptedRole Role user attempted to escalate to
   */
  public void logPrivilegeEscalationAttempt(
      String username, String ipAddress, String currentRole, String attemptedRole) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.error(
        "Security PRIVILEGE ESCALATION ATTEMPT [user={}] ip={} from={} to={}",
        sanitizedUsername,
        ipAddress,
        currentRole,
        attemptedRole);
  }

  /**
   * Logs a session timeout at INFO level.
   *
   * <p>Format: {@code Security SESSION TIMEOUT [user=X]}
   *
   * @param username Username or user ID
   */
  public void logSessionTimeout(String username) {
    String sanitizedUsername = sanitizer.sanitize(username);
    logger.info("Security SESSION TIMEOUT [user={}]", sanitizedUsername);
  }
}
