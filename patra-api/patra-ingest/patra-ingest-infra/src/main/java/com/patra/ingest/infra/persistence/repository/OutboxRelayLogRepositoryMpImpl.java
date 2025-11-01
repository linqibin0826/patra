package com.patra.ingest.infra.persistence.repository;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import com.patra.ingest.domain.port.OutboxRelayLogRepository;
import com.patra.ingest.infra.persistence.converter.OutboxRelayLogConverter;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import com.patra.ingest.infra.persistence.mapper.OutboxRelayLogMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-Plus implementation for Outbox relay log persistence.
 *
 * <h3>Responsibilities</h3>
 *
 * <ul>
 *   <li>Persist relay execution logs (single or batch) to database
 *   <li>Query logs by message, batch, channel, status, or time range
 *   <li>Support troubleshooting, monitoring, and analytics use cases
 * </ul>
 *
 * <h3>Design Principles</h3>
 *
 * <ul>
 *   <li><strong>Append-only</strong>: Logs are never updated after creation (immutable audit trail)
 *   <li><strong>Batch-optimized</strong>: Batch insert uses single SQL statement for performance
 *   <li><strong>Query-optimized</strong>: Methods leverage database indexes for efficient retrieval
 *   <li><strong>No business logic</strong>: Pure data access layer, delegates to Domain for rules
 * </ul>
 *
 * <h3>Performance Considerations</h3>
 *
 * <ul>
 *   <li>Batch insert: Use {@link #saveBatch(List)} for 100-500 logs (single INSERT statement)
 *   <li>Index coverage: All query methods use appropriate indexes (see Mapper JavaDoc)
 *   <li>Pagination: Query methods accept limit parameter to prevent large result sets
 * </ul>
 *
 * <h3>Logging Strategy</h3>
 *
 * <ul>
 *   <li>DEBUG: Batch insert operation (size and affected rows)
 *   <li>DEBUG: Query operations (method name and result count)
 *   <li>No INFO logging for high-frequency operations (avoid log noise)
 * </ul>
 *
 * <h3>Thread Safety</h3>
 *
 * <p>No shared mutable state (Mapper/Converter are stateless or thread-safe); instance can be
 * reused across threads.
 *
 * @author Papertrace Team
 * @since 2.0
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class OutboxRelayLogRepositoryMpImpl implements OutboxRelayLogRepository {

  private final OutboxRelayLogMapper mapper;
  private final OutboxRelayLogConverter converter;

  /**
   * Persists a single relay log to database.
   *
   * <p>Use case: Single relay execution (rare, typically use batch insert instead).
   *
   * <p>Performance note: For multiple logs, prefer {@link #saveBatch(List)} to reduce DB
   * round-trips.
   *
   * @param relayLog relay log to persist
   */
  @Override
  public void save(OutboxRelayLog relayLog) {
    if (relayLog == null) {
      throw new IllegalArgumentException("OutboxRelayLog must not be null");
    }

    OutboxRelayLogDO entity = converter.toEntity(relayLog);
    mapper.insert(entity);

    // No logging for single insert (use saveBatch for high-frequency operations)
  }

  /**
   * Batch persists multiple relay logs in a single SQL statement.
   *
   * <p>Use case: Relay job completes 100-500 messages, insert all logs at once.
   *
   * <p>Performance: Single INSERT statement with multiple rows (e.g., INSERT INTO ... VALUES
   * (row1), (row2), ...).
   *
   * <p>Recommended batch size: 100-500 logs (beyond 500 may hit SQL length limits).
   *
   * @param logs list of relay logs to persist
   */
  @Override
  public void saveBatch(List<OutboxRelayLog> logs) {
    if (logs == null || logs.isEmpty()) {
      return;
    }

    List<OutboxRelayLogDO> entities = converter.toEntities(logs);
    int rows = mapper.insertBatch(entities);

    log.debug(
        "Batch saved relay logs: batchSize={}, affectedRows={}, batchIds={}",
        logs.size(),
        rows,
        logs.stream().map(OutboxRelayLog::getRelayBatchId).distinct().toList());
  }

  /**
   * Queries all relay logs for a specific outbox message (newest first).
   *
   * <p>Use case: Troubleshooting - "show all relay attempts for message X".
   *
   * <p>Index used: <code>idx_message_id(message_id, started_at DESC)</code>.
   *
   * @param messageId outbox message ID
   * @return list of relay logs (newest first)
   */
  @Override
  public List<OutboxRelayLog> findByOutboxMessageId(Long messageId) {
    if (messageId == null) {
      throw new IllegalArgumentException("messageId must not be null");
    }

    List<OutboxRelayLogDO> entities = mapper.findByMessageId(messageId);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug("Found {} relay logs for messageId={}", logs.size(), messageId);
    return logs;
  }

  /**
   * Queries all relay logs for a specific batch (oldest first).
   *
   * <p>Use case: Batch-level statistics - "how did batch X perform?".
   *
   * <p>Index used: <code>idx_batch_id(relay_batch_id)</code>.
   *
   * @param batchId relay batch identifier (format: yyyyMMddHHmmss-xxxxxxxx)
   * @return list of relay logs (oldest first)
   */
  @Override
  public List<OutboxRelayLog> findByBatchId(String batchId) {
    if (batchId == null || batchId.isBlank()) {
      throw new IllegalArgumentException("batchId must not be null or blank");
    }

    List<OutboxRelayLogDO> entities = mapper.findByBatchId(batchId);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug("Found {} relay logs for batchId={}", logs.size(), batchId);
    return logs;
  }

  /**
   * Counts relay logs matching channel, status, and time range.
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Monitoring dashboard: "success rate for INGEST channel in last 1 hour"
   *   <li>Alert queries: "how many FAILED relays in last 10 minutes?"
   * </ul>
   *
   * <p>Index used: <code>idx_channel_time</code> or <code>idx_status</code>.
   *
   * @param channel channel name filter (null = all channels)
   * @param status relay status filter (null = all statuses)
   * @param startTime time range start (inclusive)
   * @param endTime time range end (exclusive)
   * @return count of matching relay logs
   */
  @Override
  public long countByChannelAndStatus(
      String channel, String status, Instant startTime, Instant endTime) {
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("startTime and endTime must not be null");
    }

    long count = mapper.countByChannelAndStatus(channel, status, startTime, endTime);

    log.debug(
        "Counted {} relay logs for channel={}, status={}, timeRange=[{}, {}]",
        count,
        channel,
        status,
        startTime,
        endTime);
    return count;
  }

  /**
   * Queries recent failed relay logs (for alerting).
   *
   * <p>Use case: On-call triage - "show top 10 recent failures".
   *
   * <p>Index used: <code>idx_status(relay_status='FAILED', started_at DESC)</code>.
   *
   * @param channel channel name filter (null = all channels)
   * @param limit maximum number of logs to return
   * @return list of failed relay logs (newest first)
   */
  @Override
  public List<OutboxRelayLog> findRecentFailed(String channel, int limit) {
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }

    List<OutboxRelayLogDO> entities = mapper.findRecentFailed(channel, limit);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug("Found {} recent failed relay logs for channel={}", logs.size(), channel);
    return logs;
  }

  /**
   * Queries relay logs for a channel within a time range.
   *
   * <p>Use case: Historical review - "show all relay attempts for INGEST channel today".
   *
   * <p>Index used: <code>idx_channel_time(channel, started_at)</code>.
   *
   * @param channel channel name filter (null = all channels)
   * @param startTime time range start (inclusive)
   * @param endTime time range end (exclusive)
   * @param limit maximum number of logs to return
   * @return list of relay logs (newest first)
   */
  @Override
  public List<OutboxRelayLog> findByChannelAndTimeRange(
      String channel, Instant startTime, Instant endTime, int limit) {
    if (startTime == null || endTime == null) {
      throw new IllegalArgumentException("startTime and endTime must not be null");
    }
    if (limit <= 0) {
      throw new IllegalArgumentException("limit must be > 0");
    }

    List<OutboxRelayLogDO> entities =
        mapper.findByChannelAndTimeRange(channel, startTime, endTime, limit);
    List<OutboxRelayLog> logs = converter.toDomains(entities);

    log.debug(
        "Found {} relay logs for channel={}, timeRange=[{}, {}]",
        logs.size(),
        channel,
        startTime,
        endTime);
    return logs;
  }
}
