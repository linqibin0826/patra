package com.patra.ingest.infra.adapter.persistence;

import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.vo.task.TaskId;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.infra.adapter.persistence.converter.mapper.TaskJpaMapper;
import com.patra.ingest.infra.adapter.persistence.dao.TaskDao;
import com.patra.ingest.infra.adapter.persistence.entity.TaskEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 任务（Task）仓储实现，基于 JPA。
///
/// 职责：
///
/// - 持久化和检索任务聚合根实体
/// - 按计划 ID 查询任务，支持切片重放和统计
/// - 统计队列中的待处理任务，用于检测数据源队列积压
/// - 支持租约操作，包括 CAS 获取、续约和标记为 RUNNING 状态
///
/// 日志策略：
///
/// - DEBUG：insert/update 操作，包括 id 和 planId
/// - INFO：租约获取和续约等关键事件
/// - 高频查询操作不记录日志，减少 I/O
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRepositoryAdapter implements TaskRepository {

  /// Task JPA Repository
  private final TaskDao taskDao;

  /// 聚合根与 JPA 实体转换器
  private final TaskJpaMapper taskJpaMapper;

  /// 保存任务。
  ///
  /// 根据 ID 是否存在决定插入或更新。自动生成的 ID 和 version 字段会被回写；操作类型在 DEBUG 级别记录。
  ///
  /// @param task 任务聚合根
  /// @return 持久化后的任务聚合根
  @Override
  public TaskAggregate save(TaskAggregate task) {
    TaskEntity entity = taskJpaMapper.toEntity(task);

    if (task.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      if (log.isDebugEnabled()) {
        log.debug(
            "task insert planId={} idemKey={}", entity.getPlanId(), entity.getIdempotentKey());
      }
    } else {
      // 更新：使用现有 ID 和 version
      entity.setId(task.getId().value());
      entity.setVersion(task.getVersion());
      if (log.isDebugEnabled()) {
        log.debug(
            "task update id={} planId={} status={}",
            entity.getId(),
            entity.getPlanId(),
            entity.getStatusCode());
      }
    }

    TaskEntity saved = taskDao.save(entity);
    task.assignId(TaskId.of(saved.getId()));
    Long version = saved.getVersion();
    task.assignVersion(version == null ? task.getVersion() : version);
    return task;
  }

  /// 批量保存任务。
  ///
  /// 对新增任务使用 JPA `saveAll()` 批量插入，对已有任务逐条更新（保持乐观锁语义）。
  /// 确保 version 和 ID 回写一致性。
  ///
  /// @param tasks 任务列表
  /// @return 持久化后的任务集合
  @Override
  public List<TaskAggregate> saveAll(List<TaskAggregate> tasks) {
    if (tasks == null || tasks.isEmpty()) {
      return List.of();
    }

    // 分离新增和更新
    List<TaskAggregate> toInsert = new ArrayList<>();
    List<TaskAggregate> toUpdate = new ArrayList<>();

    for (TaskAggregate task : tasks) {
      if (task.getId() == null) {
        toInsert.add(task);
      } else {
        toUpdate.add(task);
      }
    }

    // 批量插入新任务
    if (!toInsert.isEmpty()) {
      List<TaskEntity> insertEntities = new ArrayList<>(toInsert.size());
      for (TaskAggregate task : toInsert) {
        TaskEntity entity = taskJpaMapper.toEntity(task);
        entity.setId(SnowflakeIdGenerator.getId());
        insertEntities.add(entity);
      }

      List<TaskEntity> savedEntities = taskDao.saveAll(insertEntities);

      // 回写 ID 和 version
      for (int i = 0; i < toInsert.size(); i++) {
        TaskAggregate task = toInsert.get(i);
        TaskEntity saved = savedEntities.get(i);
        task.assignId(TaskId.of(saved.getId()));
        task.assignVersion(saved.getVersion() != null ? saved.getVersion() : 0L);
      }

      if (log.isDebugEnabled()) {
        log.debug("批量插入任务 {} 条", toInsert.size());
      }
    }

    // 逐条更新已有任务（保持乐观锁语义）
    for (TaskAggregate task : toUpdate) {
      save(task);
    }

    // 合并返回（保持原始顺序）
    return tasks;
  }

  /// 根据计划 ID 查找任务。
  ///
  /// @param planId 计划 ID
  /// @return 任务聚合根列表，如果没有则为空
  @Override
  public List<TaskAggregate> findByPlanId(Long planId) {
    if (planId == null) {
      return List.of();
    }
    List<TaskEntity> entities = taskDao.findByPlanId(planId);

    if (entities.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug("query tasks by planId={}, found 0 results", planId);
      }
      return List.of();
    }
    if (log.isDebugEnabled()) {
      log.debug("query tasks by planId={}, found {} results", planId, entities.size());
    }
    return entities.stream().map(taskJpaMapper::toAggregate).toList();
  }

  /// 查找与特定切片关联的任务（强制 1:1 关系）。
  ///
  /// Slice:Task 是 1:1 关系，由数据库唯一约束 `uk_task_slice` 保护。
  ///
  /// @param sliceId 切片 ID
  /// @return 任务聚合根（如果存在），否则为 {@link Optional#empty()}
  /// @throws IllegalArgumentException 如果 sliceId 为 null
  @Override
  public Optional<TaskAggregate> findBySliceId(Long sliceId) {
    if (sliceId == null) {
      throw new IllegalArgumentException("sliceId must not be null");
    }

    List<TaskEntity> entities = taskDao.findBySliceId(sliceId);
    if (entities.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(taskJpaMapper.toAggregate(entities.get(0)));
  }

  /// 根据任务 ID 查找任务。
  ///
  /// @param taskId 任务 ID
  /// @return 任务聚合根，如果未找到则为空
  @Override
  public Optional<TaskAggregate> findById(Long taskId) {
    if (taskId == null) {
      return Optional.empty();
    }
    return taskDao.findById(taskId).map(taskJpaMapper::toAggregate);
  }

  /// 统计 QUEUED 状态的任务数量，可按数据源和操作过滤。
  ///
  /// @param provenanceCode 数据源代码，为 null 表示不过滤
  /// @param operationCode 操作代码，为 null 表示不过滤
  /// @return 排队任务数量
  @Override
  public long countQueuedTasks(ProvenanceCode provenanceCode, String operationCode) {
    List<TaskEntity> queuedTasks = taskDao.findByStatusCode("QUEUED");

    long count =
        queuedTasks.stream()
            .filter(
                t ->
                    provenanceCode == null
                        || provenanceCode.getCode().equals(t.getProvenanceCode()))
            .filter(t -> operationCode == null || operationCode.equals(t.getOperationCode()))
            .count();

    if (log.isDebugEnabled()) {
      log.debug(
          "count queued tasks provenance={} operation={}, count={}",
          provenanceCode,
          operationCode,
          count);
    }
    return count;
  }

  /// 使用 CAS 操作尝试获取租约。
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约所有者标识符
  /// @param now 当前时间（UTC）
  /// @param ttlSeconds 租约 TTL（秒）
  /// @param idempotentKey 幂等键（防御性验证）
  /// @return 成功获取租约返回 true，如果被他人持有或条件不满足返回 false
  @Override
  public boolean tryAcquireLease(
      Long taskId, String owner, Instant now, int ttlSeconds, String idempotentKey) {
    Instant leaseExpireAt = now.plusSeconds(ttlSeconds);
    int affected = taskDao.tryAcquireLease(taskId, owner, now, leaseExpireAt, idempotentKey);
    if (affected > 0) {
      log.info("task lease acquired taskId={} owner={}", taskId, owner);
    } else {
      if (log.isDebugEnabled()) {
        log.debug("task lease miss taskId={} owner={}", taskId, owner);
      }
    }
    return affected > 0;
  }

  /// 标记任务为 RUNNING 并更新租约。
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约所有者
  /// @param now 当前时间
  /// @param ttlSeconds 租约 TTL（秒）
  /// @return 更新成功返回 true，租约丢失返回 false
  @Override
  public boolean markRunningWithLease(Long taskId, String owner, Instant now, int ttlSeconds) {
    Instant leaseExpireAt = now.plusSeconds(ttlSeconds);
    int affected = taskDao.markRunningWithLease(taskId, owner, now, leaseExpireAt);
    if (affected > 0) {
      log.info("task marked RUNNING taskId={} owner={}", taskId, owner);
    } else {
      log.warn("task lease lost on markRunning taskId={} owner={}", taskId, owner);
    }
    return affected > 0;
  }

  /// 通过心跳续约租约。
  ///
  /// @param taskId 任务 ID
  /// @param owner 租约所有者
  /// @param now 当前时间
  /// @param ttlSeconds 租约 TTL（秒）
  /// @return 续约成功返回 true，租约丢失返回 false
  @Override
  public boolean renewLease(Long taskId, String owner, Instant now, int ttlSeconds) {
    Instant leaseExpireAt = now.plusSeconds(ttlSeconds);
    int affected = taskDao.renewLease(taskId, owner, now, leaseExpireAt);
    if (affected > 0) {
      if (log.isDebugEnabled()) {
        log.debug("task lease renewed taskId={} owner={}", taskId, owner);
      }
    } else {
      log.warn("task lease lost on renew taskId={} owner={}", taskId, owner);
    }
    return affected > 0;
  }

  /// 批量通过心跳续约租约（性能优化）。
  ///
  /// @param taskIds 任务 ID 列表
  /// @param owner 租约所有者
  /// @param now 当前时间
  /// @param ttlSeconds 租约 TTL（秒）
  /// @return 成功续约的任务数量
  @Override
  public int batchRenewLeases(List<Long> taskIds, String owner, Instant now, int ttlSeconds) {
    if (taskIds == null || taskIds.isEmpty()) {
      return 0;
    }
    Instant leaseExpireAt = now.plusSeconds(ttlSeconds);
    int affected = taskDao.batchRenewLeases(taskIds, owner, now, leaseExpireAt);
    if (affected > 0) {
      if (log.isDebugEnabled()) {
        log.debug(
            "batch lease renewed count={} owner={} requestedCount={}",
            affected,
            owner,
            taskIds.size());
      }
    } else {
      log.warn("batch lease renewal failed owner={} requestedCount={}", owner, taskIds.size());
    }
    return affected;
  }
}
