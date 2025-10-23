package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.exception.OutboxPersistenceException;
import com.patra.ingest.domain.model.entity.OutboxMessage;
import com.patra.ingest.domain.port.OutboxMessageRepository;
import com.patra.ingest.domain.port.OutboxRelayStore;
import com.patra.ingest.infra.persistence.converter.OutboxMessageConverter;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import com.patra.ingest.infra.persistence.mapper.OutboxMessageMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus implementation for Outbox message persistence.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>Initial message write (PENDING state) with idempotency via (channel, dedupKey) uniqueness
 *   <li>Fetch publishable messages by channel and availability window (no locking, filter by
 *       status=PENDING and availableAt &lt;= now)
 *   <li>Compete for distributed "publish rights" via optimistic locking + lease fields
 *       (leaseOwner/leaseExpireAt/version)
 *   <li>Advance state based on publish results: PUBLISHED / DEFERRED (retry back to PENDING) /
 *       FAILED (terminal)
 * </ul>
 *
 * <h3>State Machine (Simplified)</h3>
 *
 * <pre>
 *   PENDING --(acquireLease success)--> LEASED --(markPublished)--> PUBLISHED (terminal)
 *             |
 *             |--(publish fail, retryable → markDeferred)--> PENDING (retryCount+1, wait until nextRetryAt)
 *             |--(publish fail, exhausted → markFailed)-----> FAILED (terminal)
 *
 * Note: "LEASED" intermediate state is not explicitly stored (implicitly represented by leaseOwner!=null
 *       & leaseExpireAt not expired + version condition). fetchPending only returns records not held by
 *       "active leases" (SQL filters expired or null leases).
 * </pre>
 *
 * <h3>Concurrency Control</h3>
 *
 * <ul>
 *   <li>Version number prevents concurrent overwrites via conditional updates; acquireLease/mark*
 *       use version as optimistic lock
 *   <li>Lease consists of two elements: leaseOwner/leaseExpireAt; once acquired, message is
 *       invisible to other consumers until expiry
 *   <li>If publish process crashes or times out, expired lease allows new consumer to take over
 * </ul>
 *
 * <h3>Idempotency</h3>
 *
 * <ul>
 *   <li>Upstream ensures no duplicate writes via (channel, dedupKey) during Outbox insertion; this
 *       repository provides query support
 *   <li>After successful publish, markPublished only takes effect when version matches; duplicate
 *       calls (version changed) trigger exception for upstream idempotent confirmation
 * </ul>
 *
 * <h3>Error Handling Strategy</h3>
 *
 * <ul>
 *   <li>Affected rows != 1 indicates version conflict or missing record: throws {@link
 *       OutboxPersistenceException} for upstream decision (ignore/alert)
 *   <li>No INFO logging on high-frequency paths; DEBUG only for key state transitions
 * </ul>
 *
 * <h3>Logging Strategy</h3>
 *
 * <p>DEBUG level records key state transitions (acquire success, publish/defer/fail) to avoid noise
 * in batch loops; no business WARN output.
 *
 * <h3>Thread Safety</h3>
 *
 * <p>No shared mutable state (Mapper/Converter are stateless or thread-safe); instance can be
 * reused across threads.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxMessageRepositoryMpImpl implements OutboxMessageRepository, OutboxRelayStore {

  private final OutboxMessageMapper mapper;
  private final OutboxMessageConverter converter;

  /**
   * Batch saves (insert-only) Outbox messages in PENDING state.
   *
   * <p>No deduplication performed: caller must handle (channel, dedupKey) idempotency before
   * insertion.
   *
   * <p>Logging: DEBUG level records batch size to reduce noise; no per-message logging.
   *
   * @param messages Message collection (null/empty ignored)
   */
  @Override
  public void saveAll(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox batch insert size={} firstChannel={}",
          messages.size(),
          messages.get(0).getChannel());
    }
    for (OutboxMessage message : messages) {
      // Convert to data object and insert via MyBatis-Plus
      OutboxMessageDO entity = converter.toEntity(message);
      mapper.insert(entity);
    }
  }

  /**
   * Inserts or updates a single Outbox message.
   *
   * <p>Use cases: Compensatory writes or updating non-state fields (rare). For regular state
   * transitions, use dedicated mark* methods to ensure version semantics.
   *
   * @param message Message (null ignored)
   */
  @Override
  public void saveOrUpdate(OutboxMessage message) {
    if (message == null) {
      return;
    }
    OutboxMessageDO entity = converter.toEntity(message);
    if (entity.getId() == null) {
      mapper.insert(entity);
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbox insert channel={} dedupKey={} id={}",
            message.getChannel(),
            message.getDedupKey(),
            entity.getId());
      }
    } else {
      // Update non-state fields only (e.g., payload/headers); state transitions handled by mark*
      // methods
      mapper.updateById(entity);
      if (log.isDebugEnabled()) {
        log.debug(
            "Outbox update id={} channel={} version={} (non-state fields)",
            entity.getId(),
            message.getChannel(),
            message.getVersion());
      }
    }
  }

  /**
   * Finds a message by (channel, dedupKey) for idempotency checks.
   *
   * @param channel Channel identifier (must not be null)
   * @param dedupKey Deduplication key (must not be null)
   * @return Optional containing message if found, empty otherwise
   */
  @Override
  public Optional<OutboxMessage> findByChannelAndDedup(String channel, String dedupKey) {
    OutboxMessageDO entity = mapper.findByChannelAndDedup(channel, dedupKey);
    return Optional.ofNullable(entity).map(converter::toDomain);
  }

  // ==================== OutboxRelayStore Implementation ====================

  /**
   * Fetches pending messages ready for publishing.
   *
   * <p>Supports channel filtering or fetching from all channels:
   *
   * <ul>
   *   <li>When channel is non-null, fetches messages from specified channel only
   *   <li>When channel is null, fetches messages from all channels
   * </ul>
   *
   * <p>Filter criteria: state=PENDING AND available_at &lt;= :availableTime AND (lease_owner IS
   * NULL OR lease_expire_at &lt; NOW).
   *
   * <p>No ordering guarantee enforced here; defined by Mapper layer (recommended: available_at, id
   * ASC).
   *
   * @param channel Channel identifier, null to fetch from all channels
   * @param availableTime Availability time upper bound (<=), typically current time
   * @param limit Maximum number of messages (&lt;=0 returns empty list)
   * @return List of pending messages (may be empty)
   */
  @Override
  public List<OutboxMessage> fetchPending(String channel, Instant availableTime, int limit) {
    if (limit <= 0) {
      return Collections.emptyList();
    }
    List<OutboxMessageDO> entities = mapper.fetchPending(channel, availableTime, limit);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }
    // Map to domain objects for upstream Relay processing
    return entities.stream().map(converter::toDomain).toList();
  }

  /**
   * Acquires lease via optimistic locking.
   *
   * <p>Condition: id = ? AND version = :expectedVersion AND (lease_owner IS NULL OR lease_expire_at
   * &lt; NOW).
   *
   * <p>Success: Updates leaseOwner/leaseExpireAt/version=version+1, returns true; Failure: Returns
   * false (possibly acquired by others or version conflict).
   *
   * <p>Logging: DEBUG on success only; failure is normal competition, not logged.
   *
   * @param id Message ID
   * @param expectedVersion Expected version (must read current value before calling)
   * @param leaseOwner Lease owner identifier (recommended: instance ID)
   * @param leaseExpireAt Lease expiration timestamp
   * @return true if lease acquired successfully, false otherwise
   */
  @Override
  public boolean acquireLease(
      Long id, Long expectedVersion, String leaseOwner, Instant leaseExpireAt) {
    int affectedRows = mapper.acquireLease(id, expectedVersion, leaseOwner, leaseExpireAt);
    boolean isSuccess = affectedRows == 1;
    if (isSuccess && log.isDebugEnabled()) {
      log.debug(
          "Outbox lease acquired id={} owner={} expireAt={}",
          id,
          leaseOwner,
          leaseExpireAt);
    }
    return isSuccess;
  }

  /**
   * Marks message as successfully published.
   *
   * <p>Requires current version == expectedVersion; updates fields: state=PUBLISHED, published_at,
   * broker_message_id, version=version+1.
   *
   * @param id Message ID
   * @param expectedVersion Expected version (includes post-lease version)
   * @param messageId Broker-returned message ID (for idempotent confirmation)
   * @throws OutboxPersistenceException if version conflict or row not found
   */
  @Override
  public void markPublished(Long id, Long expectedVersion, String messageId) {
    int affectedRows = mapper.markPublished(id, expectedVersion, messageId);
    if (affectedRows != 1) {
      throw new OutboxPersistenceException(
          OutboxPersistenceException.Stage.MARK_PUBLISHED,
          "Failed to update Outbox state to PUBLISHED, id=" + id);
    }
    if (log.isDebugEnabled()) {
      log.debug("Outbox published id={} brokerMsgId={}", id, messageId);
    }
  }

  /**
   * Marks message for deferred retry: state reverts to PENDING (or logically remains PENDING),
   * records nextRetryAt and error info, increments version.
   *
   * <p>Upstream must have already decided retry is allowed (retryCount not exceeded threshold).
   *
   * @param id Message ID
   * @param expectedVersion Expected version
   * @param retryCount New retry count
   * @param nextRetryAt Next retry availability time
   * @param errorCode Error code (optional)
   * @param errorMessage Error message (may be truncated)
   * @throws OutboxPersistenceException if version conflict or write failure
   */
  @Override
  public void markDeferred(
      Long id,
      Long expectedVersion,
      int retryCount,
      Instant nextRetryAt,
      String errorCode,
      String errorMessage) {
    // Use optimistic lock conditional update to revert state and record next retry plan
    int affectedRows =
        mapper.markDeferred(id, expectedVersion, retryCount, nextRetryAt, errorCode, errorMessage);
    if (affectedRows != 1) {
      throw new OutboxPersistenceException(
          OutboxPersistenceException.Stage.MARK_RETRY, "Failed to mark Outbox for retry, id=" + id);
    }
    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox deferred id={} retryCount={} nextRetryAt={} errCode={}",
          id,
          retryCount,
          nextRetryAt,
          errorCode);
    }
  }

  /**
   * Marks message as permanently failed (FAILED/DEAD state).
   *
   * <p>Terminal state: no longer fetched; upstream may choose manual compensation or move to
   * dead-letter store.
   *
   * @param id Message ID
   * @param expectedVersion Expected version
   * @param retryCount Final retry count (for audit)
   * @param errorCode Error code
   * @param errorMessage Error message
   * @throws OutboxPersistenceException if version conflict or row not found
   */
  @Override
  public void markFailed(
      Long id, Long expectedVersion, int retryCount, String errorCode, String errorMessage) {
    int affectedRows = mapper.markFailed(id, expectedVersion, retryCount, errorCode, errorMessage);
    if (affectedRows != 1) {
      throw new OutboxPersistenceException(
          OutboxPersistenceException.Stage.MARK_DEAD, "Failed to mark Outbox as DEAD, id=" + id);
    }
    if (log.isDebugEnabled()) {
      int errorMsgLength = errorMessage == null ? 0 : errorMessage.length();
      log.debug(
          "Outbox failed id={} retryCount={} errCode={} errMsgLen={}",
          id,
          retryCount,
          errorCode,
          errorMsgLength);
    }
  }

  // ==================== OutboxMessageRepository: Batch Operations ====================

  /**
   * Batch queries Outbox messages by channel and deduplication keys.
   *
   * <p>Used for batch idempotency checks in publishRetry scenarios.
   *
   * @param channel Channel identifier
   * @param dedupKeys Deduplication key collection (recommended &lt;=500 to avoid IN clause
   *     performance issues)
   * @return List of matching messages, empty list if no matches
   */
  @Override
  public List<OutboxMessage> findByChannelAndDedupIn(String channel, List<String> dedupKeys) {
    if (dedupKeys == null || dedupKeys.isEmpty()) {
      return Collections.emptyList();
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox batch query channel={} dedupKeyCount={}",
          channel,
          dedupKeys.size());
    }

    List<OutboxMessageDO> entities = mapper.findByChannelAndDedupIn(channel, dedupKeys);
    if (entities == null || entities.isEmpty()) {
      return Collections.emptyList();
    }

    return entities.stream().map(converter::toDomain).toList();
  }

  /**
   * Batch updates Outbox messages (for compensatory publish scenarios with state refresh).
   *
   * <p>Typical scenario: Retry resets existing message state to PENDING, updates payload/headers,
   * resets retry count.
   *
   * <p>Note: Uses MyBatis-Plus updateById per-record; suitable for small batches (&lt;100
   * messages).
   *
   * @param messages Message collection to update (must contain valid IDs)
   */
  @Override
  public void updateBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug("Outbox batch update size={}", messages.size());
    }

    for (OutboxMessage message : messages) {
      OutboxMessageDO entity = converter.toEntity(message);
      if (entity.getId() == null) {
        throw new IllegalArgumentException(
            "Cannot update Outbox message without ID, dedupKey=" + message.getDedupKey());
      }
      mapper.updateById(entity);
    }
  }

  /**
   * Batch inserts or updates Outbox messages (UPSERT semantics).
   *
   * <p>Implements idempotency via unique constraint (channel + dedupKey):
   *
   * <ul>
   *   <li>If message does not exist, inserts new record
   *   <li>If message exists (dedupKey conflict), updates payload/headers/status and resets
   *       retryCount
   * </ul>
   *
   * <p>Solves publishRetry concurrency race conditions (two instances retrying same message
   * simultaneously).
   *
   * @param messages Message collection to insert or update
   */
  @Override
  public void upsertBatch(List<OutboxMessage> messages) {
    if (messages == null || messages.isEmpty()) {
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox upsert batch size={} firstChannel={}",
          messages.size(),
          messages.get(0).getChannel());
    }

    List<OutboxMessageDO> entities = messages.stream().map(converter::toEntity).toList();

    int affectedRows = mapper.upsertBatch(entities);

    if (log.isDebugEnabled()) {
      log.debug(
          "Outbox upsert batch completed size={} affectedRows={}",
          messages.size(),
          affectedRows);
    }
  }
}
