package com.patra.ingest.app.usecase.relay.coordinator;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.model.vo.relay.RelayPlan;
import com.patra.ingest.domain.policy.RelayErrorClassifier;
import com.patra.ingest.domain.policy.RelayErrorClassifier.RelayErrorKind;
import com.patra.ingest.domain.policy.RelayRetryPolicy;
import com.patra.ingest.domain.port.OutboxPublisherPort;
import com.patra.ingest.domain.port.OutboxRelayStore;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Relay publish coordinator - manages message publishing and state transitions.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>Publish outbox messages to downstream broker via OutboxPublisherPort
 *   <li>Classify publishing errors (FATAL vs TRANSIENT) using RelayErrorClassifier
 *   <li>Compute retry timing using RelayRetryPolicy with exponential backoff
 *   <li>Drive state transitions (PUBLISHING → PUBLISHED/FAILED)
 * </ul>
 *
 * <h3>Publishing Flow</h3>
 *
 * <ol>
 *   <li>Call {@code publisherPort.publish(message, plan)}
 *   <li>On success → {@link #handleSuccess(OutboxMessage)}
 *   <li>On exception → Classify error:
 *       <ul>
 *         <li>TRANSIENT + can retry → {@link #handleRetry(OutboxMessage, RelayPlan, Exception)}
 *         <li>FATAL or max retries → {@link #handleFailed(OutboxMessage, RelayPlan, Exception)}
 *       </ul>
 * </ol>
 *
 * <h3>State Transitions</h3>
 *
 * <pre>
 * PUBLISHING → PUBLISHED  (success, lease released)
 * PUBLISHING → FAILED     (transient error, retry scheduled, lease released)
 * PUBLISHING → DEAD       (fatal error or max retries, lease released)
 * </pre>
 *
 * <h3>Retry Logic</h3>
 *
 * <ul>
 *   <li>Uses exponential backoff via RelayRetryPolicy
 *   <li>Respects maxAttempts from RelayPlan
 *   <li>Computes nextRetryAt = now + backoff(attemptNumber)
 * </ul>
 *
 * <h3>Logging Strategy</h3>
 *
 * <ul>
 *   <li>DEBUG: Successful publish (messageId, channel, duration)
 *   <li>WARN: Transient error with retry (messageId, attemptNumber, nextRetryAt, errorCode)
 *   <li>ERROR: Fatal error or max retries (messageId, totalAttempts, errorCode, exception)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelayPublishCoordinator {

  private final OutboxRelayStore relayStore;
  private final OutboxPublisherPort publisherPort;
  private final RelayErrorClassifier errorClassifier;
  private final RelayRetryPolicy retryPolicy;

  /**
   * Publishes a single outbox message and handles the result.
   *
   * <p>This method orchestrates the entire publish lifecycle:
   *
   * <ol>
   *   <li>Attempt to publish via {@code publisherPort}
   *   <li>On success, mark as PUBLISHED and release lease
   *   <li>On failure, classify error and either retry or fail permanently
   * </ol>
   *
   * @param message outbox message with lease already acquired
   * @param plan relay plan containing retry policy parameters
   * @return relay result indicating success/retry/failed outcome
   */
  public RelayResult publish(OutboxMessage message, RelayPlan plan) {
    Instant startTime = Instant.now();
    int attemptNumber = message.computeNextAttempt();

    try {
      // Attempt to publish to downstream broker
      publisherPort.publish(message, plan);

      // Success: Mark as PUBLISHED and release lease
      handleSuccess(message);

      if (log.isDebugEnabled()) {
        long durationMs = Duration.between(startTime, Instant.now()).toMillis();
        log.debug(
            "Published successfully: messageId={}, channel={}, attemptNumber={}, durationMs={}",
            message.getId(),
            message.getChannel(),
            attemptNumber,
            durationMs);
      }

      return RelayResult.success(message.getId(), attemptNumber);

    } catch (Exception exception) {
      // Classify error to determine retry eligibility
      RelayErrorKind errorKind = errorClassifier.classify(exception);
      boolean canRetry = message.canRetry(plan.maxAttempts());
      String errorCode = extractErrorCode(exception);
      String errorMessage = truncateMessage(exception.getMessage(), 500);

      if (errorKind == RelayErrorKind.TRANSIENT && canRetry) {
        // Transient error with retry budget remaining
        handleRetry(message, plan, exception);

        Duration backoff = retryPolicy.computeDelay(attemptNumber);
        Instant nextRetryAt = Instant.now().plus(backoff);

        log.warn(
            "Publishing failed with transient error, will retry: messageId={}, channel={}, attemptNumber={}/{}, nextRetryAt={}, errorCode={}, errorMessage={}",
            message.getId(),
            message.getChannel(),
            attemptNumber,
            plan.maxAttempts(),
            nextRetryAt,
            errorCode,
            truncateMessage(exception.getMessage(), 200));

        return RelayResult.deferred(
            message.getId(), attemptNumber, nextRetryAt, errorCode, errorMessage);

      } else {
        // Fatal error or max retries exhausted
        handleFailed(message, plan, exception);

        String reason =
            errorKind == RelayErrorKind.FATAL
                ? "fatal error"
                : "max retries exhausted (" + plan.maxAttempts() + ")";

        log.error(
            "Publishing failed permanently: messageId={}, channel={}, attemptNumber={}, reason={}, errorCode={}, errorMessage={}",
            message.getId(),
            message.getChannel(),
            attemptNumber,
            reason,
            errorCode,
            truncateMessage(exception.getMessage(), 200),
            exception);

        return RelayResult.failed(message.getId(), attemptNumber, errorCode, errorMessage);
      }
    }
  }

  /**
   * Handles successful publishing by marking message as PUBLISHED.
   *
   * <p>State transition: PUBLISHING → PUBLISHED
   *
   * <p>Database changes:
   *
   * <ul>
   *   <li>status_code = 'PUBLISHED'
   *   <li>published_at = NOW(6)
   *   <li>pub_lease_owner = NULL (lease released)
   *   <li>pub_leased_until = NULL (lease released)
   *   <li>error_code = NULL (cleared)
   *   <li>error_msg = NULL (cleared)
   *   <li>next_retry_at = NULL (cleared)
   *   <li>version = version + 1
   * </ul>
   *
   * @param message outbox message that was published successfully
   */
  private void handleSuccess(OutboxMessage message) {
    relayStore.markPublished(message.getId(), message.getVersion());
  }

  /**
   * Handles transient publishing errors by scheduling retry.
   *
   * <p>State transition: PUBLISHING → FAILED (with next_retry_at set)
   *
   * <p>Database changes:
   *
   * <ul>
   *   <li>status_code = 'FAILED' (but will retry based on next_retry_at)
   *   <li>retry_count = incremented
   *   <li>next_retry_at = computed using exponential backoff
   *   <li>error_code = extracted from exception
   *   <li>error_msg = truncated exception message
   *   <li>pub_lease_owner = NULL (lease released)
   *   <li>pub_leased_until = NULL (lease released)
   *   <li>version = version + 1
   * </ul>
   *
   * @param message outbox message that failed with transient error
   * @param plan relay plan containing retry policy parameters
   * @param exception the exception that occurred during publishing
   */
  private void handleRetry(OutboxMessage message, RelayPlan plan, Exception exception) {
    int attemptNumber = message.computeNextAttempt();
    Duration backoff = retryPolicy.computeDelay(attemptNumber);
    Instant nextRetryAt = Instant.now().plus(backoff);

    relayStore.markDeferred(
        message.getId(),
        message.getVersion(),
        attemptNumber, // Updated retry count
        nextRetryAt,
        extractErrorCode(exception),
        truncateMessage(exception.getMessage(), 500));
  }

  /**
   * Handles fatal publishing errors or max retry exhaustion.
   *
   * <p>State transition: PUBLISHING → DEAD
   *
   * <p>Database changes:
   *
   * <ul>
   *   <li>status_code = 'DEAD' (terminal state, no more retries)
   *   <li>retry_count = final attempt count
   *   <li>error_code = extracted from exception
   *   <li>error_msg = truncated exception message
   *   <li>next_retry_at = NULL (no retry)
   *   <li>pub_lease_owner = NULL (lease released)
   *   <li>pub_leased_until = NULL (lease released)
   *   <li>version = version + 1
   * </ul>
   *
   * @param message outbox message that failed permanently
   * @param plan relay plan (for context)
   * @param exception the exception that occurred during publishing
   */
  private void handleFailed(OutboxMessage message, RelayPlan plan, Exception exception) {
    int attemptNumber = message.computeNextAttempt();

    relayStore.markFailed(
        message.getId(),
        message.getVersion(),
        attemptNumber, // Final retry count
        extractErrorCode(exception),
        truncateMessage(exception.getMessage(), 500));
  }

  /**
   * Extracts a simple error code from exception class name.
   *
   * <p>Examples:
   *
   * <ul>
   *   <li>TimeoutException → TIMEOUT_EXCEPTION
   *   <li>BrokerUnavailableException → BROKER_UNAVAILABLE_EXCEPTION
   * </ul>
   *
   * @param exception the exception to extract code from
   * @return error code derived from exception class name
   */
  private String extractErrorCode(Exception exception) {
    return exception.getClass().getSimpleName().replaceAll("([a-z])([A-Z])", "$1_$2").toUpperCase();
  }

  /**
   * Truncates error message to specified max length.
   *
   * <p>Prevents excessive storage usage in database error_msg column.
   *
   * @param message error message to truncate
   * @param maxLength maximum allowed length
   * @return truncated message with "..." suffix if truncated
   */
  private String truncateMessage(String message, int maxLength) {
    if (message == null) {
      return null;
    }
    if (message.length() <= maxLength) {
      return message;
    }
    return message.substring(0, maxLength - 3) + "...";
  }

  /**
   * Result of a relay publishing attempt.
   *
   * <p>Three possible outcomes:
   *
   * <ul>
   *   <li>SUCCESS: Message published and marked PUBLISHED
   *   <li>DEFERRED: Transient error, scheduled for retry
   *   <li>FAILED: Fatal error or max retries, marked DEAD
   * </ul>
   */
  public record RelayResult(
      Long messageId,
      RelayOutcome outcome,
      int attemptNumber,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage) {

    public static RelayResult success(Long messageId, int attemptNumber) {
      return new RelayResult(messageId, RelayOutcome.SUCCESS, attemptNumber, null, null, null);
    }

    public static RelayResult deferred(
        Long messageId,
        int attemptNumber,
        Instant nextRetryAt,
        String errorCode,
        String errorMessage) {
      return new RelayResult(
          messageId, RelayOutcome.DEFERRED, attemptNumber, nextRetryAt, errorCode, errorMessage);
    }

    public static RelayResult failed(
        Long messageId, int attemptNumber, String errorCode, String errorMessage) {
      return new RelayResult(
          messageId, RelayOutcome.FAILED, attemptNumber, null, errorCode, errorMessage);
    }

    public boolean isSuccess() {
      return outcome == RelayOutcome.SUCCESS;
    }

    public boolean isDeferred() {
      return outcome == RelayOutcome.DEFERRED;
    }

    public boolean isFailed() {
      return outcome == RelayOutcome.FAILED;
    }

    public enum RelayOutcome {
      SUCCESS,
      DEFERRED,
      FAILED
    }
  }
}
