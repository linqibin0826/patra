package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.time.Instant;
import java.util.List;

/**
 * Persistence port used by the outbox relay to fetch publishable messages and drive state
 * transitions.
 *
 * <p>Abstracts storage interactions so the relay can acquire leases, record publish outcomes, and
 * schedule retries.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxRelayStore {

  /**
   * Fetch pending outbox messages.
   *
   * <p>Supports optional channel filtering:
   *
   * <ul>
   *   <li>If {@code channel} is non-null, restrict to that channel.
   *   <li>If {@code channel} is null, fetch across all channels.
   * </ul>
   *
   * @param channel channel identifier or {@code null} for all channels
   * @param availableTime reference time used to determine publish eligibility
   * @param limit maximum number of messages to retrieve
   * @return pending messages ordered per implementation; empty when none qualify
   */
  List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit);

  /**
   * Acquire a lease for the given message and mark it in-progress.
   *
   * @param id outbox identifier
   * @param expectedVersion optimistic-lock version (nullable to skip the check)
   * @param leaseOwner lease owner marker, usually with scheduler context
   * @param leaseExpireAt lease expiration time that frees the message for other instances
   * @return {@code true} when acquired successfully; {@code false} otherwise
   */
  boolean acquireLease(Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt);

  /**
   * Mark the message as published and store the downstream message identifier.
   *
   * @param id outbox identifier
   * @param expectedVersion optimistic-lock version
   * @param messageId broker-provided message identifier (optional)
   */
  void markPublished(Long id, Long expectedVersion, String messageId);

  /**
   * Requeue the message for retry after a recoverable failure.
   *
   * @param id outbox identifier
   * @param expectedVersion optimistic-lock version
   * @param retryCount number of attempts already performed
   * @param nextRetryAt next allowed retry time
   * @param errorCode error classification for metrics
   * @param errorMessage error description
   */
  void markDeferred(
      Long id,
      Long expectedVersion,
      int retryCount,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage);

  /**
   * Mark the message as dead once retries are exhausted.
   *
   * @param id outbox identifier
   * @param expectedVersion optimistic-lock version
   * @param retryCount number of attempts made
   * @param errorCode error classification
   * @param errorMessage error description
   */
  void markFailed(
      Long id, Long expectedVersion, int retryCount, String errorCode, String errorMessage);
}
