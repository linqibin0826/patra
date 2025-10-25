package com.patra.ingest.api.error;

import com.patra.common.error.codes.ErrorCodeLike;

/**
 * Catalog of error codes for the ingest service.
 *
 * <p>Error code format: {@code ING-NNNN} where ING is the service prefix.
 *
 * <p>Code ranges:
 *
 * <ul>
 *   <li>0xxx: HTTP-aligned errors (use {@code HttpStdErrors.of("ING").*} factory methods)
 *   <li>12xx: Registry configuration errors
 *   <li>13xx: Outbox persistence errors
 *   <li>14xx: Scheduler and job execution errors
 *   <li>15xx: Checkpoint serialization errors
 *   <li>16xx: Plan assembly errors
 * </ul>
 *
 * @see com.patra.common.error.codes.ErrorCodeLike
 */
public enum IngestErrorCode implements ErrorCodeLike {

  // ===== Registry Configuration Errors (12xx) =====

  /**
   * Signals that provenance configuration is missing in registry.
   *
   * <p>Occurs when requesting configuration for a provenance code that does not exist in the
   * registry service. Verify the provenance code is correctly registered in patra-registry.
   */
  ING_1201("ING-1201", 404),

  /**
   * Signals that registry returned malformed or invalid configuration data.
   *
   * <p>Occurs when registry response fails validation or contains inconsistent data. Check registry
   * data integrity and schema compliance.
   */
  ING_1202("ING-1202", 422),

  /**
   * Signals that registry service is unreachable or degraded.
   *
   * <p>Occurs when registry service is down or experiencing issues. The system may fall back to
   * cached or default configurations. Monitor registry service health.
   */
  ING_1203("ING-1203", 503),

  // ===== Outbox Persistence Errors (13xx) =====

  /**
   * Signals failure to persist domain event to outbox table.
   *
   * <p>Occurs during transaction commit when outbox message insert fails. Indicates database
   * connectivity or constraint violation issues. Check database logs and connection pool.
   */
  ING_1301("ING-1301", 500),

  /**
   * Signals failure to update outbox message status.
   *
   * <p>Occurs when attempting to mark outbox message as published or failed. May indicate
   * optimistic locking conflicts or database issues. Retry mechanism should handle transient
   * failures.
   */
  ING_1302("ING-1302", 500),

  /**
   * Signals failure to move outbox message to dead-letter queue.
   *
   * <p>Occurs when maximum retry attempts exceeded but dead-letter persistence fails. Requires
   * manual intervention to prevent message loss. Check database and dead-letter table schema.
   */
  ING_1303("ING-1303", 500),

  // ===== Scheduler and Job Execution Errors (14xx) =====

  /**
   * Signals that scheduled job parameters are invalid or malformed.
   *
   * <p>Occurs when job parameters fail JSON parsing or business validation. Verify job
   * configuration and parameter schema. Common causes include missing required fields or invalid
   * date formats.
   */
  ING_1401("ING-1401", 422),

  /**
   * Signals unexpected failure during scheduled job execution.
   *
   * <p>Occurs when job encounters unhandled exceptions during execution. Check application logs for
   * stack traces and root cause. May indicate external service failures or data corruption.
   */
  ING_1402("ING-1402", 500),

  /**
   * Signals that plan assembly pre-validation failed.
   *
   * <p>Occurs when input parameters for plan creation fail business rules validation before
   * assembly begins. Common causes include invalid date ranges, negative batch sizes, or disabled
   * provenance sources. Verify input parameters meet business constraints.
   */
  ING_1403("ING-1403", 422),

  // ===== Checkpoint Serialization Errors (15xx) =====

  /**
   * Signals failure to deserialize checkpoint payload from storage.
   *
   * <p>Occurs when loading checkpoint data that is corrupted, incompatible, or in wrong format. May
   * indicate schema evolution issues or data corruption. Verify checkpoint format version
   * compatibility.
   */
  ING_1501("ING-1501", 422),

  /**
   * Signals failure to serialize checkpoint payload for storage.
   *
   * <p>Occurs when checkpoint object cannot be converted to JSON or binary format. May indicate
   * circular references, unsupported types, or encoding issues. Check checkpoint data structure.
   */
  ING_1502("ING-1502", 422),

  // ===== Plan Persistence Errors (15xx) =====

  /**
   * Signals failure to persist batch plan and associated tasks to database.
   *
   * <p>Occurs during transactional save of plan entity and task entities. Common causes include
   * constraint violations, deadlocks, or connection timeouts. Check database logs and transaction
   * isolation settings.
   */
  ING_1503("ING-1503", 500),

  // ===== Plan Assembly Errors (16xx) =====

  /**
   * Signals failure to assemble batch plan with required tasks and slices.
   *
   * <p>Occurs when plan assembly logic fails to generate valid task slices. May indicate issues
   * with date range calculations, batch size logic, or data availability. Verify assembly
   * parameters and provenance configuration.
   */
  ING_1601("ING-1601", 500);

  private final String code;
  private final int httpStatus;

  IngestErrorCode(String code, int httpStatus) {
    this.code = code;
    this.httpStatus = httpStatus;
  }

  /**
   * Returns the error code string.
   *
   * @return error code in format ING-NNNN
   */
  @Override
  public String code() {
    return code;
  }

  /**
   * Returns the HTTP status code associated with this error.
   *
   * @return HTTP status code (400-599)
   */
  @Override
  public int httpStatus() {
    return httpStatus;
  }

  /**
   * Returns the error code string representation.
   *
   * @return error code in format ING-NNNN
   */
  @Override
  public String toString() {
    return code;
  }
}
