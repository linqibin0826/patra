package dev.linqibin.patra.ingest.infra.adapter.persistence.dao;

import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.TaskEntity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// 采集任务 JPA Repository。
///
/// **职责**：
///
/// - 提供 TaskEntity 的 CRUD 操作
/// - 支持租约相关的 CAS 操作（获取、续约、标记运行中）
/// - 提供按状态、切片等多种查询
///
/// @author linqibin
/// @since 0.1.0
public interface TaskDao extends JpaRepository<TaskEntity, Long> {

  /// 根据幂等键查找任务。
  ///
  /// @param idempotentKey 幂等键
  /// @return 任务实体
  Optional<TaskEntity> findByIdempotentKey(String idempotentKey);

  /// 检查幂等键是否存在。
  ///
  /// @param idempotentKey 幂等键
  /// @return 存在返回 true
  boolean existsByIdempotentKey(String idempotentKey);

  /// 根据切片 ID 查找所有任务。
  ///
  /// @param sliceId 切片 ID
  /// @return 任务列表
  List<TaskEntity> findBySliceId(Long sliceId);

  /// 根据状态查找任务。
  ///
  /// @param statusCode 状态代码
  /// @return 任务列表
  List<TaskEntity> findByStatusCode(String statusCode);

  /// 根据计划 ID 查找任务。
  ///
  /// @param planId 计划 ID
  /// @return 任务列表
  List<TaskEntity> findByPlanId(Long planId);

  /// CAS 租约获取（步骤 0）。
  ///
  /// 仅更新满足调度和租约接管条件的 QUEUED 状态任务。
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约拥有者 ID
  /// @param now 当前时间（UTC）
  /// @param leaseExpireAt 租约过期时间
  /// @param idempotentKey 幂等键（防御性检查）
  /// @return 受影响行数（1=成功，0=失败）
  @Modifying
  @Query(
      """
      UPDATE TaskEntity t
      SET t.leaseOwner = :owner,
          t.leasedUntil = :leaseExpireAt,
          t.leaseCount = COALESCE(t.leaseCount, 0) + 1,
          t.version = t.version + 1
      WHERE t.id = :taskId
        AND t.idempotentKey = :idempotentKey
        AND t.statusCode = 'QUEUED'
        AND (t.scheduledAt IS NULL OR t.scheduledAt <= :now)
        AND (t.leaseOwner IS NULL OR t.leasedUntil < :now)
      """)
  int tryAcquireLease(
      @Param("taskId") Long taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("leaseExpireAt") Instant leaseExpireAt,
      @Param("idempotentKey") String idempotentKey);

  /// 标记任务为 RUNNING 并更新租约（步骤 1）。
  ///
  /// 前置条件：WHERE lease_owner=#{owner}
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约拥有者
  /// @param now 当前时间
  /// @param leaseExpireAt 租约过期时间
  /// @return 受影响行数（1=成功，0=租约丢失）
  @Modifying
  @Query(
      """
      UPDATE TaskEntity t
      SET t.statusCode = 'RUNNING',
          t.startedAt = :now,
          t.leasedUntil = :leaseExpireAt,
          t.lastHeartbeatAt = :now,
          t.version = t.version + 1
      WHERE t.id = :taskId
        AND t.leaseOwner = :owner
      """)
  int markRunningWithLease(
      @Param("taskId") Long taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("leaseExpireAt") Instant leaseExpireAt);

  /// 心跳租约续约。
  ///
  /// 前置条件：WHERE lease_owner=#{owner}
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约拥有者
  /// @param now 当前时间
  /// @param leaseExpireAt 租约过期时间
  /// @return 受影响行数（1=成功，0=租约丢失）
  @Modifying
  @Query(
      """
      UPDATE TaskEntity t
      SET t.leasedUntil = :leaseExpireAt,
          t.lastHeartbeatAt = :now,
          t.version = t.version + 1
      WHERE t.id = :taskId
        AND t.leaseOwner = :owner
      """)
  int renewLease(
      @Param("taskId") Long taskId,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("leaseExpireAt") Instant leaseExpireAt);

  /// 批量心跳租约续约（性能优化）。
  ///
  /// 前置条件：WHERE id IN (taskIds) AND lease_owner=#{owner}
  ///
  /// @param taskIds 任务 ID 列表
  /// @param owner 租约拥有者
  /// @param now 当前时间
  /// @param leaseExpireAt 租约过期时间
  /// @return 受影响行数（已续约的任务数）
  @Modifying
  @Query(
      """
      UPDATE TaskEntity t
      SET t.leasedUntil = :leaseExpireAt,
          t.lastHeartbeatAt = :now,
          t.version = t.version + 1
      WHERE t.id IN :taskIds
        AND t.leaseOwner = :owner
      """)
  int batchRenewLeases(
      @Param("taskIds") Collection<Long> taskIds,
      @Param("owner") String owner,
      @Param("now") Instant now,
      @Param("leaseExpireAt") Instant leaseExpireAt);
}
