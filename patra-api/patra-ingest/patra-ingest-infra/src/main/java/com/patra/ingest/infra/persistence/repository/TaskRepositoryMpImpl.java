package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.aggregate.TaskAggregate;
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

/**
 * 基于 MyBatis-Plus 的任务仓储实现。
 *
 * <p>职责：
 *
 * <ul>
 *   <li>任务聚合（TaskAggregate）的持久化与回读。
 *   <li>按计划 ID 查询任务集合（用于切片回放 / 统计）。
 *   <li>统计排队状态的任务数量（队列背压判断输入来源）。
 *   <li>支持租约相关操作（CAS 抢占、续租、置 RUNNING）。
 * </ul>
 *
 * 日志策略：
 *
 * <ul>
 *   <li>DEBUG：插入 / 更新操作（含 id / planId）。
 *   <li>INFO：租约抢占与续租关键节点。
 *   <li>不打印高频查询日志，减少 I/O。
 * </ul>
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRepositoryMpImpl implements TaskRepository {

  /** 任务 Mapper */
  private final TaskMapper mapper;

  /** 任务转换器 */
  private final TaskConverter converter;

  /**
   * 保存任务（insert 或 update）。
   *
   * <p>回写自增 ID 与版本字段；DEBUG 打印操作类型。
   */
  @Override
  public TaskAggregate save(TaskAggregate task) {
    TaskDO entity = converter.toEntity(task);
    if (entity.getId() == null) {
      if (log.isDebugEnabled()) {
        log.debug(
            "task insert planId={} idemKey={}",
            entity.getPlanId(),
            entity.getIdempotentKey());
      }
      mapper.insert(entity);
      task.assignId(entity.getId());
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

  /**
   * 批量保存任务（顺序调用 {@link #save(TaskAggregate)}，确保版本/ID 写回一致性）。
   *
   * @param tasks 任务列表
   * @return 持久化后任务集合
   */
  @Override
  public List<TaskAggregate> saveAll(List<TaskAggregate> tasks) {
    List<TaskAggregate> persisted = new ArrayList<>(tasks.size());
    for (TaskAggregate task : tasks) {
      persisted.add(save(task));
    }
    return persisted;
  }

  /**
   * 根据计划 ID 查询任务集合。
   *
   * @param planId 计划 ID
   * @return 任务聚合列表（无则空列表）
   */
  @Override
  public List<TaskAggregate> findByPlanId(Long planId) {
    if (planId == null) {
      return List.of();
    }
    List<TaskDO> entities = mapper.selectList(new QueryWrapper<TaskDO>().eq("plan_id", planId));
    if (entities == null || entities.isEmpty()) {
      return List.of();
    }
    List<TaskAggregate> aggregates = new ArrayList<>(entities.size());
    for (TaskDO entity : entities) {
      aggregates.add(converter.toAggregate(entity));
    }
    return aggregates;
  }

  /**
   * 按任务 ID 查询任务聚合。
   *
   * @param taskId 任务 ID
   * @return 任务聚合，不存在则返回 empty
   */
  @Override
  public Optional<TaskAggregate> findById(Long taskId) {
    if (taskId == null) {
      return Optional.empty();
    }
    TaskDO entity = mapper.selectById(taskId);
    return entity == null ? Optional.empty() : Optional.of(converter.toAggregate(entity));
  }

  /**
   * 统计处于 QUEUED 状态的任务数量（可选按来源 / 操作过滤）。
   *
   * @param provenanceCode 来源代码，可空
   * @param operationCode 操作代码，可空
   * @return 数量
   */
  @Override
  public long countQueuedTasks(String provenanceCode, String operationCode) {
    QueryWrapper<TaskDO> wrapper = new QueryWrapper<>();
    wrapper.eq("status_code", "QUEUED");
    if (provenanceCode != null) {
      wrapper.eq("provenance_code", provenanceCode);
    }
    if (operationCode != null) {
      wrapper.eq("operation_code", operationCode);
    }
    return mapper.selectCount(wrapper);
  }

  /**
   * CAS 抢占租约（步骤 0）。
   *
   * @param taskId 任务 ID
   * @param owner 租约持有者标识
   * @param now 当前时间（UTC）
   * @param ttlSeconds 租约 TTL（秒）
   * @param idempotentKey 幂等键（用于防御性校验）
   * @return true 表示抢占成功，false 表示他人持有或条件不满足
   */
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

  /**
   * 置任务为 RUNNING 状态并更新租约（步骤 1）。
   *
   * @param taskId 任务 ID
   * @param owner 租约持有者
   * @param now 当前时间
   * @param ttlSeconds 租约 TTL（秒）
   * @return true 表示更新成功，false 表示租约已丢失
   */
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

  /**
   * 心跳续租。
   *
   * @param taskId 任务 ID
   * @param owner 租约持有者
   * @param now 当前时间
   * @param ttlSeconds 租约 TTL（秒）
   * @return true 表示续租成功，false 表示租约已丢失
   */
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

  /**
   * 批量心跳续租（性能优化）。
   *
   * @param taskIds 任务ID列表
   * @param owner 租约持有者
   * @param now 当前时间
   * @param ttlSeconds 租约 TTL（秒）
   * @return 成功续租的任务数
   */
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
      log.warn(
          "batch lease renewal failed owner={} requestedCount={}",
          owner,
          taskIds.size());
    }
    return affected;
  }
}
