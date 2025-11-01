package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.OutboxRelayLog;
import java.time.Instant;
import java.util.List;

/**
 * Repository port for OutboxRelayLog persistence and querying.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Persist relay execution logs (single or batch)
 *   <li>Query logs by message, batch, or time range
 *   <li>Support monitoring and troubleshooting queries
 * </ul>
 *
 * <p>Design principles:
 *
 * <ul>
 *   <li>Port interface in Domain layer (no infrastructure dependencies)
 *   <li>Implementation in Infrastructure layer (MyBatis-Plus repository)
 *   <li>Domain-centric signatures (uses domain entities, not DOs)
 * </ul>
 *
 * @author Papertrace Team
 * @since 2.0
 */
public interface OutboxRelayLogRepository {

  /**
   * Persists a single relay log.
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Single relay execution result
   *   <li>Ad-hoc relay attempts outside batch jobs
   * </ul>
   *
   * @param log relay log to persist
   */
  void save(OutboxRelayLog log);

  /**
   * Persists multiple relay logs in a single batch operation.
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Batch relay job results (typical case: 100-500 logs)
   *   <li>Performance optimization: single INSERT statement
   * </ul>
   *
   * <p>Implementation note: Must use batch INSERT (NOT N individual INSERTs).
   *
   * @param logs list of relay logs to persist
   */
  void saveBatch(List<OutboxRelayLog> logs);

  /**
   * Queries all relay logs for a specific outbox message (ordered by time descending).
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Troubleshooting: "Why did this message fail?"
   *   <li>Audit trail: "How many times was this message retried?"
   * </ul>
   *
   * @param messageId outbox message ID
   * @return list of relay logs (newest first)
   */
  List<OutboxRelayLog> findByOutboxMessageId(Long messageId);

  /**
   * Queries all relay logs for a specific batch (ordered by time ascending).
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Batch-level statistics: "How did batch X perform?"
   *   <li>Performance analysis: "What was the success rate for this batch?"
   * </ul>
   *
   * @param batchId relay batch identifier (format: yyyyMMddHHmmss-xxxxxxxx)
   * @return list of relay logs (oldest first)
   */
  List<OutboxRelayLog> findByBatchId(String batchId);

  /**
   * Counts relay logs matching channel, status, and time range.
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Monitoring dashboard: "Success rate for INGEST channel in last 1 hour"
   *   <li>Alert queries: "How many failures in last 10 minutes?"
   * </ul>
   *
   * @param channel channel name (e.g., INGEST), null = all channels
   * @param status relay status (e.g., PUBLISHED), null = all statuses
   * @param startTime time range start (inclusive)
   * @param endTime time range end (exclusive)
   * @return count of matching relay logs
   */
  long countByChannelAndStatus(String channel, String status, Instant startTime, Instant endTime);

  /**
   * Queries recent failed relay logs (for alerting).
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Alert notification: "Show top 10 recent failures"
   *   <li>On-call triage: "What's failing right now?"
   * </ul>
   *
   * @param channel channel name filter (null = all channels)
   * @param limit maximum number of logs to return
   * @return list of failed relay logs (newest first)
   */
  List<OutboxRelayLog> findRecentFailed(String channel, int limit);

  /**
   * Queries relay logs for a channel within a time range (for analysis).
   *
   * <p>Use cases:
   *
   * <ul>
   *   <li>Performance analysis: "Show all relay attempts today"
   *   <li>Historical review: "How did yesterday's relay perform?"
   * </ul>
   *
   * @param channel channel name filter (null = all channels)
   * @param startTime time range start (inclusive)
   * @param endTime time range end (exclusive)
   * @param limit maximum number of logs to return
   * @return list of relay logs (ordered by startedAt descending)
   */
  List<OutboxRelayLog> findByChannelAndTimeRange(
      String channel, Instant startTime, Instant endTime, int limit);
}
