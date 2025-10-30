package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * Outbox message Mapper interface.
 *
 * <p>Contains conditional updates for state progression, lease acquisition, retry/failure marking.
 *
 * <p>Concurrency: all writes rely on <code>expectedVersion</code> for optimistic locking; callers
 * must check affected rows.
 *
 * <p>Index assumptions (for SQL optimization):
 *
 * <ul>
 *   <li><code>uk_outbox_channel_dedup(channel, dedup_key)</code> idempotent unique constraint
 *   <li><code>idx_outbox_status_time(status_code, not_before, id)</code> batch scan pending
 *   <li><code>idx_outbox_lease(status_code, pub_leased_until)</code> lease expiry filter
 *   <li><code>idx_outbox_partition(channel, partition_key, status_code)</code> partition/order
 * </ul>
 */
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageDO> {

  /** Idempotent lookup by (channel, dedupKey). */
  OutboxMessageDO findByChannelAndDedup(
      @Param("channel") String channel, @Param("dedupKey") String dedupKey);

  /**
   * Fetch publishable messages (PENDING and time-eligible and not leased).
   *
   * <p>If channel is not null, only fetch for that channel; otherwise fetch for all channels.
   */
  List<OutboxMessageDO> fetchPending(
      @Param("channel") String channel,
      @Param("available") Instant available,
      @Param("limit") int limit);

  /**
   * Acquire lease with optimistic locking and increment version. Condition example: id=? AND
   * version=:expectedVersion AND (pub_lease_owner IS NULL OR pub_leased_until < NOW).
   */
  int acquireLease(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("leaseOwner") String leaseOwner,
      @Param("leaseExpireAt") Instant leaseExpireAt);

  /** Marks as published and increments version. */
  int markPublished(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

  /** Marks as deferred (retry) and increments version. */
  int markDeferred(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("retryCount") int retryCount,
      @Param("nextRetryAt") Instant nextRetryAt,
      @Param("errorCode") String errorCode,
      @Param("errorMsg") String errorMsg);

  /** Marks as terminal failure (FAILED/DEAD) and increments version. */
  int markFailed(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("retryCount") int retryCount,
      @Param("errorCode") String errorCode,
      @Param("errorMsg") String errorMsg);

  /** Batch query messages by channel and a set of dedup keys (≤500 recommended). */
  List<OutboxMessageDO> findByChannelAndDedupIn(
      @Param("channel") String channel, @Param("dedupKeys") List<String> dedupKeys);

  /**
   * Batch UPSERT for Outbox messages.
   *
   * <p>Idempotent by unique constraint (channel, dedup_key). Inserts when missing; updates
   * payload_json/headers_json/status_code and resets retry_count on conflict. Solves race
   * conditions in publishRetry (two instances retrying the same message).
   */
  int upsertBatch(@Param("messages") List<OutboxMessageDO> messages);
}
