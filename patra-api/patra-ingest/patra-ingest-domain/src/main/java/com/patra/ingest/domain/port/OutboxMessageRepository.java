package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxMessage;
import java.util.List;
import java.util.Optional;

/**
 * Repository port for outbox messages.
 *
 * <p>Persists pending messages, enforces idempotency, and enables bulk operations. Typically
 * coordinates with {@link OutboxRelayStore} to manage the message lifecycle.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface OutboxMessageRepository {

  /**
   * Persist a batch of outbox messages.
   *
   * @param messages messages to store
   */
  void saveAll(List<OutboxMessage> messages);

  /**
   * Create or update a single outbox message.
   *
   * @param message message entity containing idempotent key, payload, and status
   */
  void saveOrUpdate(OutboxMessage message);

  /**
   * Locate an existing message by channel and idempotent key, typically for deduplication.
   *
   * @param channel channel identifier
   * @param dedupKey idempotent key
   * @return matching message if present, otherwise {@link Optional#empty()}
   */
  Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey);

  /**
   * Retrieve messages by channel and a list of idempotent keys.
   *
   * <p>Supports batch idempotency checks during compensation workflows (for example, {@code
   * publishRetry}).
   *
   * @param channel channel identifier
   * @param dedupKeys idempotent keys (keep the list reasonably small to avoid large {@code IN}
   *     clauses)
   * @return matching messages or an empty list when none found
   */
  List<OutboxMessage> findByChannelAndDedupIn(String channel, List<String> dedupKeys);

  /**
   * Update a batch of outbox messages (for example, during compensation to refresh state).
   *
   * <p>Common use: reset existing messages to {@code PENDING}, refresh payload/headers, and reset
   * retry counts.
   *
   * @param messages messages to update (must carry valid identifiers)
   */
  void updateBatch(List<OutboxMessage> messages);

  /**
   * Perform batch insert-or-update (upsert) on outbox messages.
   *
   * <p>Enforces idempotency via the unique constraint on {@code channel + dedupKey}:
   *
   * <ul>
   *   <li>Insert a new record when no match exists.
   *   <li>Update payload/headers/status and reset retry count when a deduplication key already
   *       exists.
   * </ul>
   *
   * <p>Designed to avoid race conditions when multiple nodes retry the same message concurrently.
   *
   * @param messages messages to insert or update
   */
  void upsertBatch(List<OutboxMessage> messages);
}
