package com.patra.starter.logging.persistence;

import com.patra.common.logging.sanitizer.LogSanitizer;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility for standardized logging of database operation failures.
 *
 * <p>Implements FR-007 (Database Failures Logging) and SC-008 (100% Audit Logging) for database
 * operations.
 *
 * <h3>Purpose:</h3>
 *
 * Provides consistent logging format for database failures:
 *
 * <ul>
 *   <li>Failed queries (SELECT, INSERT, UPDATE, DELETE)
 *   <li>Transaction failures and rollbacks
 *   <li>Connection pool exhaustion
 *   <li>Deadlocks and lock timeouts
 *   <li>Constraint violations
 *   <li>SQL errors with sanitized query text
 * </ul>
 *
 * <h3>Usage Example (Manual):</h3>
 *
 * <pre>{@code
 * // In repository implementation
 * private static final Logger log = LoggerFactory.getLogger(MyRepository.class);
 * private final DbFailureLogger dbLogger;
 *
 * public void saveArticle(Article article) {
 *     long startTime = System.currentTimeMillis();
 *     try {
 *         articleMapper.insert(article);
 *     } catch (DataAccessException e) {
 *         Duration duration = Duration.ofMillis(System.currentTimeMillis() - startTime);
 *         dbLogger.logQueryFailure("INSERT", "article", duration, e);
 *         throw e;
 *     }
 * }
 * }</pre>
 *
 * <h3>Usage Example (Automatic via Interceptor):</h3>
 *
 * Prefer using {@link DbFailureLoggingInterceptor} for automatic logging of all MyBatis-Plus
 * operations.
 *
 * <h3>Log Format:</h3>
 *
 * <pre>
 * ERROR Database operation FAILED [operation=INSERT] table=article duration=125ms: Duplicate key violation
 * ERROR Database TRANSACTION ROLLBACK [transactionName=saveArticleWithMetadata] duration=350ms: Constraint violation
 * ERROR Database CONNECTION FAILURE: Connection pool exhausted (max=20, active=20)
 * </pre>
 *
 * <h3>Sanitization:</h3>
 *
 * Automatically sanitizes sensitive data in SQL queries:
 *
 * <ul>
 *   <li>Password values: {@code password='secret'} → {@code password='***REDACTED***'}
 *   <li>API keys: {@code api_key='xxx'} → {@code api_key='***REDACTED***'}
 *   <li>Email addresses in WHERE clauses
 * </ul>
 *
 * @see DbFailureLoggingInterceptor
 * @see LogSanitizer
 * @since 0.1.0 (Phase 6 - User Story 4)
 */
public class DbFailureLogger {

  private static final Logger log = LoggerFactory.getLogger(DbFailureLogger.class);

  private final Logger logger;
  private final LogSanitizer sanitizer;

  /**
   * Creates a DbFailureLogger with the given logger and sanitizer.
   *
   * @param logger The SLF4J logger for the calling class
   * @param sanitizer Log sanitizer for redacting sensitive data in SQL
   */
  public DbFailureLogger(Logger logger, LogSanitizer sanitizer) {
    this.logger = logger;
    this.sanitizer = sanitizer;
  }

  /**
   * Logs a failed database query at ERROR level.
   *
   * <p>Format: {@code Database operation FAILED [operation=X] table=Y duration=Zms: error message}
   *
   * @param operation SQL operation type (SELECT, INSERT, UPDATE, DELETE)
   * @param table Table or entity name
   * @param duration Query execution duration
   * @param error The exception that caused the failure
   */
  public void logQueryFailure(String operation, String table, Duration duration, Throwable error) {
    logger.error(
        "Database operation FAILED [operation={}] table={} duration={}ms: {}",
        operation,
        table,
        duration.toMillis(),
        error.getMessage(),
        error);
  }

  /**
   * Logs a failed database query with SQL text at ERROR level.
   *
   * <p>Format: {@code Database operation FAILED [operation=X] table=Y duration=Zms SQL: [sanitized
   * SQL] error: message}
   *
   * <p><strong>WARNING:</strong> SQL text is sanitized but may still leak table/column structure.
   * Only log SQL at ERROR level for production diagnosis, not at DEBUG/TRACE.
   *
   * @param operation SQL operation type
   * @param table Table or entity name
   * @param sql Raw SQL query (will be sanitized)
   * @param duration Query execution duration
   * @param error The exception that caused the failure
   */
  public void logQueryFailureWithSql(
      String operation, String table, String sql, Duration duration, Throwable error) {
    String sanitizedSql = sanitizer.sanitize(sql);
    logger.error(
        "Database operation FAILED [operation={}] table={} duration={}ms SQL: [{}] error: {}",
        operation,
        table,
        duration.toMillis(),
        sanitizedSql,
        error.getMessage(),
        error);
  }

  /**
   * Logs a transaction rollback at ERROR level.
   *
   * <p>Format: {@code Database TRANSACTION ROLLBACK [transactionName=X] duration=Yms: error
   * message}
   *
   * @param transactionName Transaction name or method name
   * @param duration Transaction execution duration before rollback
   * @param error The exception that caused the rollback
   */
  public void logTransactionRollback(String transactionName, Duration duration, Throwable error) {
    logger.error(
        "Database TRANSACTION ROLLBACK [transactionName={}] duration={}ms: {}",
        transactionName,
        duration.toMillis(),
        error.getMessage(),
        error);
  }

  /**
   * Logs a database connection failure at ERROR level.
   *
   * <p>Format: {@code Database CONNECTION FAILURE: error message (pool stats)}
   *
   * @param error The connection exception
   */
  public void logConnectionFailure(Throwable error) {
    logger.error("Database CONNECTION FAILURE: {}", error.getMessage(), error);
  }

  /**
   * Logs connection pool exhaustion at ERROR level.
   *
   * <p>Format: {@code Database CONNECTION POOL EXHAUSTED (max=X, active=Y, idle=Z)}
   *
   * @param maxPoolSize Maximum pool size
   * @param activeConnections Current active connections
   * @param idleConnections Current idle connections
   */
  public void logConnectionPoolExhausted(
      int maxPoolSize, int activeConnections, int idleConnections) {
    logger.error(
        "Database CONNECTION POOL EXHAUSTED (max={}, active={}, idle={})",
        maxPoolSize,
        activeConnections,
        idleConnections);
  }

  /**
   * Logs a database deadlock at WARN level (retryable).
   *
   * <p>Format: {@code Database DEADLOCK detected [operation=X] table=Y: error message}
   *
   * @param operation SQL operation type
   * @param table Table or entity name
   * @param error The deadlock exception
   */
  public void logDeadlock(String operation, String table, Throwable error) {
    logger.warn(
        "Database DEADLOCK detected [operation={}] table={}: {}",
        operation,
        table,
        error.getMessage());
  }

  /**
   * Logs a database lock timeout at WARN level (retryable).
   *
   * <p>Format: {@code Database LOCK TIMEOUT [operation=X] table=Y duration=Zms: error message}
   *
   * @param operation SQL operation type
   * @param table Table or entity name
   * @param duration Time spent waiting for lock
   * @param error The timeout exception
   */
  public void logLockTimeout(String operation, String table, Duration duration, Throwable error) {
    logger.warn(
        "Database LOCK TIMEOUT [operation={}] table={} duration={}ms: {}",
        operation,
        table,
        duration.toMillis(),
        error.getMessage());
  }

  /**
   * Logs a constraint violation at WARN level (validation error).
   *
   * <p>Format: {@code Database CONSTRAINT VIOLATION [constraint=X] table=Y: error message}
   *
   * @param constraintType Constraint type (UNIQUE, FOREIGN_KEY, NOT_NULL, CHECK)
   * @param table Table or entity name
   * @param error The constraint violation exception
   */
  public void logConstraintViolation(String constraintType, String table, Throwable error) {
    logger.warn(
        "Database CONSTRAINT VIOLATION [constraint={}] table={}: {}",
        constraintType,
        table,
        error.getMessage());
  }

  /**
   * Logs a slow query at WARN level.
   *
   * <p>Format: {@code SLOW Database query [operation=X] table=Y duration=Zms (threshold=Tms)}
   *
   * @param operation SQL operation type
   * @param table Table or entity name
   * @param duration Query execution duration
   * @param threshold Slow query threshold
   */
  public void logSlowQuery(String operation, String table, Duration duration, Duration threshold) {
    logger.warn(
        "SLOW Database query [operation={}] table={} duration={}ms (threshold={}ms)",
        operation,
        table,
        duration.toMillis(),
        threshold.toMillis());
  }
}
