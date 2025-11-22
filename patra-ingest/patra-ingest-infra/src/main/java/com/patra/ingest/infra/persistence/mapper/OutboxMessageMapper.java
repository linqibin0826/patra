package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.OutboxMessageDO;
import java.time.Instant;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/// 发件箱消息 Mapper 接口 — 对发件箱消息表的数据访问操作。
/// 
/// 包含状态推进、租约获取、重试/失败标记的条件更新操作。
/// 
/// 并发控制: 所有写入依赖 `expectedVersion` 实现乐观锁;调用者必须检查受影响行数。
/// 
/// 索引假设(用于 SQL 优化):
/// 
/// - `uk_outbox_channel_dedup(channel, dedup_key)` 幂等唯一约束
///   - `idx_outbox_status_time(status_code, not_before, id)` 批量扫描待处理消息
///   - `idx_outbox_lease(status_code, pub_leased_until)` 租约过期过滤
///   - `idx_outbox_partition(channel, partition_key, status_code)` 分区/排序
/// 
/// @author linqibin
/// @since 0.1.0
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageDO> {

  /// 根据(channel, dedupKey)幂等查找
  OutboxMessageDO findByChannelAndDedup(
      @Param("channel") String channel, @Param("dedupKey") String dedupKey);

  /// 获取可发布的消息(PENDING 状态且时间满足且未租约)。
/// 
/// 如果 channel 非空,仅获取该通道的消息;否则获取所有通道。
  List<OutboxMessageDO> fetchPending(
      @Param("channel") String channel,
      @Param("available") Instant available,
      @Param("limit") int limit);

  /// 使用乐观锁获取租约并递增版本号。
/// 
/// 条件示例: id=? AND version=:expectedVersion AND (pub_lease_owner IS NULL OR pub_leased_until
/// &lt; NOW)。
  int acquireLease(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("leaseOwner") String leaseOwner,
      @Param("leaseExpireAt") Instant leaseExpireAt);

  /// 标记为已发布并递增版本号
  int markPublished(@Param("id") Long id, @Param("expectedVersion") Long expectedVersion);

  /// 标记为延迟(重试)并递增版本号
  int markDeferred(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("retryCount") int retryCount,
      @Param("nextRetryAt") Instant nextRetryAt,
      @Param("errorCode") String errorCode,
      @Param("errorMsg") String errorMsg);

  /// 标记为终态失败(FAILED/DEAD)并递增版本号
  int markFailed(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("retryCount") int retryCount,
      @Param("errorCode") String errorCode,
      @Param("errorMsg") String errorMsg);

  /// 根据 channel 和一组 dedup keys 批量查询消息(建议 ≤500)
  List<OutboxMessageDO> findByChannelAndDedupIn(
      @Param("channel") String channel, @Param("dedupKeys") List<String> dedupKeys);

  /// 发件箱消息批量 UPSERT。
/// 
/// 通过唯一约束(channel, dedup_key)实现幂等。缺失时插入;冲突时更新 payload_json/headers_json/status_code 并重置
/// retry_count。解决 publishRetry 中的竞态条件(两个实例重试同一消息)。
  int upsertBatch(@Param("messages") List<OutboxMessageDO> messages);
}
