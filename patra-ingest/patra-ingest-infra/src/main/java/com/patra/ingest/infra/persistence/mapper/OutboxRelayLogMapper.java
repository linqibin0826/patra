package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxRelayLogDO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Outbox relay log Mapper interface.
 *
 * <p>Provides queries for relay execution audit trail, troubleshooting, and monitoring.
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li><strong>Append-only</strong>: No update methods (logs are immutable after creation)
 *   <li><strong>Batch-optimized</strong>: Batch insert method for high-throughput relay jobs
 *   <li><strong>Query-optimized</strong>: Methods aligned with common troubleshooting patterns
 * </ul>
 *
 * <p>Index assumptions (for SQL optimization):
 *
 * <ul>
 *   <li><code>idx_message_id(message_id, started_at DESC)</code>: Query logs by message
 *   <li><code>idx_batch_id(relay_batch_id)</code>: Query logs by batch
 *   <li><code>idx_channel_time(channel, started_at)</code>: Query logs by channel and time range
 *   <li><code>idx_status(relay_status, started_at)</code>: Query logs by status (for alerts)
 *   <li><code>idx_created_at(created_at)</code>: Archive old logs by creation time
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
public interface OutboxRelayLogMapper extends BaseMapper<OutboxRelayLogDO> {

  /**
   * Batch insert relay logs in a single SQL statement.
   *
   * <p>Performance note: Uses JDBC batch INSERT (single statement, multiple rows).
   *
   * <p>Use case: Relay job executes 100-500 messages, batch insert all logs at once.
   *
   * @param logs list of relay logs to insert (recommend batch size ≤ 500)
   * @return number of rows inserted
   */
  int insertBatch(@Param("logs") List<OutboxRelayLogDO> logs);

  /**
   * Queries all relay logs for a specific outbox message (ordered by started_at descending).
   *
   * <p>Use case: Troubleshooting - "show all relay attempts for message X"
   *
   * <p>Index used: <code>idx_message_id(message_id, started_at DESC)</code>
   *
   * @param messageId outbox message ID
   * @return list of relay logs (newest first)
   */
  List<OutboxRelayLogDO> findByMessageId(@Param("messageId") Long messageId);

  /**
   * Queries all relay logs for a specific batch (ordered by started_at ascending).
   *
   * <p>Use case: Batch-level statistics - "how did batch X perform?"
   *
   * <p>Index used: <code>idx_batch_id(relay_batch_id)</code>
   *
   * @param batchId relay batch identifier (format: yyyyMMddHHmmss-xxxxxxxx)
   * @return list of relay logs (oldest first)
   */
  List<OutboxRelayLogDO> findByBatchId(@Param("batchId") String batchId);

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
   * <p>Index used: <code>idx_channel_time(channel, started_at)</code> or <code>
   * idx_status(relay_status, started_at)</code>
   *
   * @param channel channel name filter (NULL = all channels)
   * @param status relay status filter (NULL = all statuses)
   * @param startTime time range start (inclusive)
   * @param endTime time range end (exclusive)
   * @return count of matching relay logs
   */
  long countByChannelAndStatus(
      @Param("channel") String channel,
      @Param("status") String status,
      @Param("startTime") Instant startTime,
      @Param("endTime") Instant endTime);

  /**
   * Queries recent failed relay logs (for alerting).
   *
   * <p>Use case: On-call triage - "show top 10 recent failures"
   *
   * <p>Index used: <code>idx_status(relay_status='FAILED', started_at DESC)</code>
   *
   * @param channel channel name filter (NULL = all channels)
   * @param limit maximum number of logs to return
   * @return list of failed relay logs (newest first)
   */
  List<OutboxRelayLogDO> findRecentFailed(
      @Param("channel") String channel, @Param("limit") int limit);

  /**
   * Queries relay logs for a channel within a time range (for analysis).
   *
   * <p>Use case: Historical review - "show all relay attempts for INGEST channel today"
   *
   * <p>Index used: <code>idx_channel_time(channel, started_at)</code>
   *
   * @param channel channel name filter (NULL = all channels)
   * @param startTime time range start (inclusive)
   * @param endTime time range end (exclusive)
   * @param limit maximum number of logs to return
   * @return list of relay logs (ordered by started_at descending)
   */
  List<OutboxRelayLogDO> findByChannelAndTimeRange(
      @Param("channel") String channel,
      @Param("startTime") Instant startTime,
      @Param("endTime") Instant endTime,
      @Param("limit") int limit);
}
