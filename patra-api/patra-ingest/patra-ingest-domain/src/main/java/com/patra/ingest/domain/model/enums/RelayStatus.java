package com.patra.ingest.domain.model.enums;

/**
 * Relay execution result enum representing the outcome of an outbox message relay attempt.
 *
 * <p>Status transitions and meanings:
 *
 * <ul>
 *   <li><strong>PUBLISHED</strong>: Message successfully published to downstream broker (terminal
 *       state)
 *   <li><strong>DEFERRED</strong>: Relay failed with retryable error, will retry later (transient
 *       state)
 *   <li><strong>FAILED</strong>: Relay failed permanently (max retries exhausted or fatal error)
 *   <li><strong>LEASE_MISSED</strong>: Failed to acquire lease due to concurrent competition
 *       (transient state)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
public enum RelayStatus {

  /** Successfully published to downstream broker (terminal state). */
  PUBLISHED("PUBLISHED", "发布成功", true, false),

  /** Deferred for retry due to transient error (will retry with backoff). */
  DEFERRED("DEFERRED", "延迟重试", false, true),

  /** Permanently failed after max retries or fatal error (terminal state). */
  FAILED("FAILED", "永久失败", true, false),

  /** Failed to acquire lease due to concurrent competition (optimistic lock failure). */
  LEASE_MISSED("LEASE_MISSED", "租约竞争失败", false, true);

  private final String code;
  private final String description;
  private final boolean terminal;
  private final boolean retryable;

  RelayStatus(String code, String description, boolean terminal, boolean retryable) {
    this.code = code;
    this.description = description;
    this.terminal = terminal;
    this.retryable = retryable;
  }

  /**
   * Gets the status code (matches database enum value).
   *
   * @return status code string
   */
  public String getCode() {
    return code;
  }

  /**
   * Gets the human-readable description (Chinese).
   *
   * @return description string
   */
  public String getDescription() {
    return description;
  }

  /**
   * Checks if this is a terminal state (no further processing needed).
   *
   * <p>Terminal states: PUBLISHED, FAILED
   *
   * @return true if this status represents a final state
   */
  public boolean isTerminal() {
    return terminal;
  }

  /**
   * Checks if this status indicates a retryable failure.
   *
   * <p>Retryable states: DEFERRED, LEASE_MISSED
   *
   * @return true if this status allows retry
   */
  public boolean isRetryable() {
    return retryable;
  }

  /**
   * Parses status code string to RelayStatus enum.
   *
   * @param code status code string (e.g., "PUBLISHED")
   * @return corresponding RelayStatus enum
   * @throws IllegalArgumentException if code is invalid
   */
  public static RelayStatus fromCode(String code) {
    if (code == null || code.isBlank()) {
      throw new IllegalArgumentException("RelayStatus code must not be null or blank");
    }
    for (RelayStatus status : values()) {
      if (status.code.equals(code)) {
        return status;
      }
    }
    throw new IllegalArgumentException("Invalid RelayStatus code: " + code);
  }
}
