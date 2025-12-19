package com.patra.ingest.infra.adapter.persistence.dao;

import com.patra.ingest.infra.adapter.persistence.entity.OutboxMessageEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 发件箱消息 JPA Repository。
///
/// **职责**：
///
/// - 提供 OutboxMessageEntity 的 CRUD 操作
/// - 支持状态推进、租约获取、重试/失败标记的条件更新操作
///
/// **并发控制**：所有写入依赖 `expectedVersion` 实现乐观锁；调用者必须检查受影响行数。
///
/// @author linqibin
/// @since 0.1.0
public interface OutboxMessageDao extends JpaRepository<OutboxMessageEntity, Long> {

  /// 根据 (channel, dedupKey) 幂等查找。
  ///
  /// @param channel 通道名称
  /// @param dedupKey 去重键
  /// @return 消息实体
  Optional<OutboxMessageEntity> findByChannelAndDedupKey(String channel, String dedupKey);

  /// 获取可发布的消息（PENDING 状态且时间满足且未租约）。
  ///
  /// @param channel 通道名称（可为 null 表示所有通道）
  /// @param available 可用时间点
  /// @param limit 返回数量限制
  /// @return 消息列表
  @Query(
      """
      SELECT m FROM OutboxMessageEntity m
      WHERE m.statusCode = 'PENDING'
        AND (m.notBefore IS NULL OR m.notBefore <= :available)
        AND (m.pubLeaseOwner IS NULL OR m.pubLeasedUntil < :available)
        AND (:channel IS NULL OR m.channel = :channel)
      ORDER BY m.id ASC
      LIMIT :limit
      """)
  List<OutboxMessageEntity> fetchPending(
      @Param("channel") String channel,
      @Param("available") Instant available,
      @Param("limit") int limit);

  /// 使用乐观锁获取租约并递增版本号。
  ///
  /// @param id 消息 ID
  /// @param expectedVersion 预期版本号
  /// @param leaseOwner 租约拥有者
  /// @param leaseExpireAt 租约过期时间
  /// @return 受影响行数
  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxMessageEntity m
      SET m.pubLeaseOwner = :leaseOwner,
          m.pubLeasedUntil = :leaseExpireAt,
          m.statusCode = 'PUBLISHING',
          m.version = m.version + 1
      WHERE m.id = :id
        AND m.version = :expectedVersion
        AND (m.pubLeaseOwner IS NULL OR m.pubLeasedUntil < CURRENT_TIMESTAMP)
      """)
  int acquireLease(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("leaseOwner") String leaseOwner,
      @Param("leaseExpireAt") Instant leaseExpireAt);

  /// 标记为已发布并递增版本号。
  ///
  /// @param id 消息 ID
  /// @param expectedVersion 预期版本号
  /// @param now 当前时间
  /// @return 受影响行数
  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxMessageEntity m
      SET m.statusCode = 'PUBLISHED',
          m.publishedAt = :now,
          m.pubLeaseOwner = NULL,
          m.pubLeasedUntil = NULL,
          m.version = m.version + 1
      WHERE m.id = :id
        AND m.version = :expectedVersion
      """)
  int markPublished(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("now") Instant now);

  /// 标记为延迟（重试）并递增版本号。
  ///
  /// @param id 消息 ID
  /// @param expectedVersion 预期版本号
  /// @param retryCount 重试次数
  /// @param nextRetryAt 下次重试时间
  /// @param errorCode 错误代码
  /// @param errorMsg 错误消息
  /// @return 受影响行数
  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxMessageEntity m
      SET m.statusCode = 'PENDING',
          m.retryCount = :retryCount,
          m.nextRetryAt = :nextRetryAt,
          m.notBefore = :nextRetryAt,
          m.errorCode = :errorCode,
          m.errorMsg = :errorMsg,
          m.pubLeaseOwner = NULL,
          m.pubLeasedUntil = NULL,
          m.version = m.version + 1
      WHERE m.id = :id
        AND m.version = :expectedVersion
      """)
  int markDeferred(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("retryCount") int retryCount,
      @Param("nextRetryAt") Instant nextRetryAt,
      @Param("errorCode") String errorCode,
      @Param("errorMsg") String errorMsg);

  /// 标记为终态失败（FAILED/DEAD）并递增版本号。
  ///
  /// @param id 消息 ID
  /// @param expectedVersion 预期版本号
  /// @param retryCount 重试次数
  /// @param errorCode 错误代码
  /// @param errorMsg 错误消息
  /// @param statusCode 目标状态（FAILED 或 DEAD）
  /// @return 受影响行数
  @Modifying(clearAutomatically = true)
  @Query(
      """
      UPDATE OutboxMessageEntity m
      SET m.statusCode = :statusCode,
          m.retryCount = :retryCount,
          m.errorCode = :errorCode,
          m.errorMsg = :errorMsg,
          m.pubLeaseOwner = NULL,
          m.pubLeasedUntil = NULL,
          m.version = m.version + 1
      WHERE m.id = :id
        AND m.version = :expectedVersion
      """)
  int markFailed(
      @Param("id") Long id,
      @Param("expectedVersion") Long expectedVersion,
      @Param("retryCount") int retryCount,
      @Param("errorCode") String errorCode,
      @Param("errorMsg") String errorMsg,
      @Param("statusCode") String statusCode);

  /// 根据 channel 和一组 dedup keys 批量查询消息。
  ///
  /// @param channel 通道名称
  /// @param dedupKeys 去重键列表
  /// @return 消息列表
  List<OutboxMessageEntity> findByChannelAndDedupKeyIn(
      String channel, Collection<String> dedupKeys);
}
