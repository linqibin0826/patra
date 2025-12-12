package com.patra.ingest.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.toolkit.Db;
import com.patra.common.enums.ProvenanceCode;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
import com.patra.ingest.domain.model.vo.task.TaskId;
import com.patra.ingest.domain.port.TaskRepository;
import com.patra.ingest.infra.persistence.converter.TaskConverter;
import com.patra.ingest.infra.persistence.entity.TaskDO;
import com.patra.ingest.infra.persistence.mapper.TaskMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 任务（Task）仓储实现,基于 MyBatis-Plus。
///
/// 职责:
///
/// - 持久化和检索任务聚合根实体
///   - 按计划 ID 查询任务,支持切片重放和统计
///   - 统计队列中的待处理任务,用于检测数据源队列积压
///   - 支持租约操作,包括 CAS 获取、续约和标记为 RUNNING 状态
///
/// 日志策略:
///
/// - DEBUG: insert/update 操作,包括 id 和 planId
///   - INFO: 租约获取和续约等关键事件
///   - 高频查询操作不记录日志,减少 I/O
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRepositoryAdapter implements TaskRepository {

  /// Task Mapper
  private final TaskMapper mapper;

  /// Task 转换器
  private final TaskConverter converter;

  /// 保存任务。
  ///
  /// 根据 ID 是否存在决定插入或更新。自动生成的 ID 和 version 字段会被回写;操作类型在 DEBUG 级别记录。
  ///
  /// @param task 任务聚合根
  /// @return 持久化后的任务聚合根
  @Override
  public TaskAggregate save(TaskAggregate task) {
    TaskDO entity = converter.toEntity(task);
    if (entity.getId() == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "task insert planId={} idemKey={}", entity.getPlanId(), entity.getIdempotentKey());
      }
      mapper.insert(entity);
      task.assignId(TaskId.of(entity.getId()));
    } else {
      if (log.isDebugEnabled()) {
        log.debug(
            "task update id={} planId={} status={}",
            entity.getId(),
            entity.getPlanId(),
            entity.getStatusCode());
      }
      mapper.updateById(entity);
    }
    Long version = entity.getVersion();
    task.assignVersion(version == null ? task.getVersion() : version);
    return task;
  }

  /// 批量保存任务。
  ///
  /// 对新增任务使用 `Db.saveBatch()` 批量插入，对已有任务逐条更新（保持乐观锁语义）。
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
      List<TaskDO> insertEntities = toInsert.stream().map(converter::toEntity).toList();

      Db.saveBatch(insertEntities);

      // 回写 ID 和 version
      for (int i = 0; i < toInsert.size(); i++) {
        TaskAggregate task = toInsert.get(i);
        TaskDO entity = insertEntities.get(i);
        task.assignId(TaskId.of(entity.getId()));
        task.assignVersion(entity.getVersion() != null ? entity.getVersion() : 0L);
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

  /// Finds tasks by plan ID.
  ///
  /// @param planId plan ID
  /// @return list of task aggregates, empty if none found
  @Override
  public List<TaskAggregate> findByPlanId(Long planId) {
    if (planId == null) {
      return List.of();
    }
    List<TaskDO> entities = mapper.selectList(new QueryWrapper<TaskDO>().eq("plan_id", planId));
    if (entities == null || entities.isEmpty()) {
      if (log.isDebugEnabled()) {
        log.debug("query tasks by planId={}, found 0 results", planId);
      }
      return List.of();
    }
    if (log.isDebugEnabled()) {
      log.debug("query tasks by planId={}, found {} results", planId, entities.size());
    }
    List<TaskAggregate> aggregates = new ArrayList<>(entities.size());
    for (TaskDO entity : entities) {
      aggregates.add(converter.toAggregate(entity));
    }
    return aggregates;
  }

  /// Finds the task associated with a specific slice (enforces 1:1 relationship).
  ///
  /// **Note:** After refactoring, Slice:Task is a 1:1 relationship protected by database
  /// unique constraint `uk_task_slice`. This method returns at most one task.
  ///
  /// @param sliceId slice ID
  /// @return task aggregate if exists, or {@link Optional#empty()}
  /// @throws IllegalArgumentException if sliceId is null
  @Override
  public Optional<TaskAggregate> findBySliceId(Long sliceId) {
    if (sliceId == null) {
      throw new IllegalArgumentException("sliceId must not be null");
    }

    LambdaQueryWrapper<TaskDO> query = new LambdaQueryWrapper<>();
    query.eq(TaskDO::getSliceId, sliceId);

    TaskDO taskDO = mapper.selectOne(query);
    return taskDO == null ? Optional.empty() : Optional.of(converter.toAggregate(taskDO));
  }

  /// Finds a task by task ID.
  ///
  /// @param taskId task ID
  /// @return task aggregate, empty if not found
  @Override
  public Optional<TaskAggregate> findById(Long taskId) {
    if (taskId == null) {
      return Optional.empty();
    }
    TaskDO entity = mapper.selectById(taskId);
    return entity == null ? Optional.empty() : Optional.of(converter.toAggregate(entity));
  }

  /// Counts tasks in QUEUED status with optional filtering by provenance and operation.
  ///
  /// @param provenanceCode provenance code, nullable for no filtering
  /// @param operationCode operation code, nullable for no filtering
  /// @return count of queued tasks
  @Override
  public long countQueuedTasks(ProvenanceCode provenanceCode, String operationCode) {
    QueryWrapper<TaskDO> wrapper = new QueryWrapper<>();
    wrapper.eq("status_code", "QUEUED");
    if (provenanceCode != null) {
      wrapper.eq("provenance_code", provenanceCode.getCode());
    }
    if (operationCode != null) {
      wrapper.eq("operation_code", operationCode);
    }
    long count = mapper.selectCount(wrapper);
    if (log.isDebugEnabled()) {
      log.debug(
          "count queued tasks provenance={} operation={}, count={}",
          provenanceCode,
          operationCode,
          count);
    }
    return count;
  }

  /// Attempts to acquire lease using CAS operation.
  ///
  /// @param taskId task ID
  /// @param owner lease owner identifier
  /// @param now current time (UTC)
  /// @param ttlSeconds lease TTL in seconds
  /// @param idempotentKey idempotency key for defensive validation
  /// @return true if lease acquired successfully, false if held by others or conditions not met
  @Override
  public boolean tryAcquireLease(
      Long taskId, String owner, Instant now, int ttlSeconds, String idempotentKey) {
    int affected = mapper.tryAcquireLease(taskId, owner, now, ttlSeconds, idempotentKey);
    if (affected > 0) {
      log.info("task lease acquired taskId={} owner={}", taskId, owner);
    } else {
      if (log.isDebugEnabled()) {
        log.debug("task lease miss taskId={} owner={}", taskId, owner);
      }
    }
    return affected > 0;
  }

  /// Marks task as RUNNING and updates lease.
  ///
  /// @param taskId task ID
  /// @param owner lease owner
  /// @param now current time
  /// @param ttlSeconds lease TTL in seconds
  /// @return true if update succeeded, false if lease lost
  @Override
  public boolean markRunningWithLease(Long taskId, String owner, Instant now, int ttlSeconds) {
    int affected = mapper.markRunningWithLease(taskId, owner, now, ttlSeconds);
    if (affected > 0) {
      log.info("task marked RUNNING taskId={} owner={}", taskId, owner);
    } else {
      log.warn("task lease lost on markRunning taskId={} owner={}", taskId, owner);
    }
    return affected > 0;
  }

  /// Renews lease via heartbeat.
  ///
  /// @param taskId task ID
  /// @param owner lease owner
  /// @param now current time
  /// @param ttlSeconds lease TTL in seconds
  /// @return true if renewal succeeded, false if lease lost
  @Override
  public boolean renewLease(Long taskId, String owner, Instant now, int ttlSeconds) {
    int affected = mapper.renewLease(taskId, owner, now, ttlSeconds);
    if (affected > 0) {
      if (log.isDebugEnabled()) {
        log.debug("task lease renewed taskId={} owner={}", taskId, owner);
      }
    } else {
      log.warn("task lease lost on renew taskId={} owner={}", taskId, owner);
    }
    return affected > 0;
  }

  /// Batch renews leases via heartbeat for performance optimization.
  ///
  /// @param taskIds list of task IDs
  /// @param owner lease owner
  /// @param now current time
  /// @param ttlSeconds lease TTL in seconds
  /// @return count of successfully renewed tasks
  @Override
  public int batchRenewLeases(List<Long> taskIds, String owner, Instant now, int ttlSeconds) {
    if (taskIds == null || taskIds.isEmpty()) {
      return 0;
    }
    int affected = mapper.batchRenewLeases(taskIds, owner, now, ttlSeconds);
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
