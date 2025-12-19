package com.patra.ingest.infra.adapter.persistence;

import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.ingest.infra.adapter.persistence.converter.mapper.TaskRunJpaMapper;
import com.patra.ingest.infra.adapter.persistence.dao.TaskRunDao;
import com.patra.ingest.infra.adapter.persistence.entity.TaskRunEntity;
import com.patra.starter.jpa.id.SnowflakeIdGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 任务执行记录（TaskRun）仓储实现，基于 JPA。
///
/// 职责：
///
/// - 插入和更新任务执行记录，包括统计数据和检查点 JSON 字段
/// - 查询任务的最新执行尝试（按 attempt_no DESC 排序，limit 1）
/// - 查询所有执行尝试，用于审计和故障排查
/// - 获取最新 attemptNo 用于生成下一次尝试编号
///
/// 设计：
///
/// - ID 为 null 时插入，否则更新
/// - 保存后通过 JPA save 返回获取数据库生成的字段（如乐观锁 version）
///
/// 日志策略：DEBUG 级别记录 insert/update 及关键字段；查询操作不记录日志。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunRepositoryAdapter implements TaskRunRepository {

  /// TaskRun JPA Repository
  private final TaskRunDao taskRunDao;

  /// 领域实体与 JPA 实体转换器
  private final TaskRunJpaMapper taskRunJpaMapper;

  /// 保存任务执行记录。
  ///
  /// @param run 任务执行实体
  /// @return 持久化后的任务执行记录，包含数据库生成的字段
  @Override
  public TaskRun save(TaskRun run) {
    TaskRunEntity entity = taskRunJpaMapper.toEntity(run);

    if (run.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
    } else {
      // 更新：使用现有 ID
      entity.setId(run.getId());
    }

    TaskRunEntity saved = taskRunDao.save(entity);
    return taskRunJpaMapper.toAggregate(saved);
  }

  /// 查找任务的最新执行尝试。
  ///
  /// @param taskId 任务 ID
  /// @return 包含最新执行记录的 Optional，如果任务从未执行则为空
  @Override
  public Optional<TaskRun> findLatest(Long taskId) {
    List<TaskRunEntity> results =
        taskRunDao.findByTaskId(taskId).stream()
            .sorted((a, b) -> Integer.compare(b.getAttemptNo(), a.getAttemptNo()))
            .limit(1)
            .toList();

    TaskRunEntity one = results.isEmpty() ? null : results.get(0);
    if (log.isDebugEnabled()) {
      log.debug(
          "query latest TaskRun by taskId={}, found={}",
          taskId,
          one != null ? "attemptNo=" + one.getAttemptNo() : "none");
    }
    return Optional.ofNullable(one).map(taskRunJpaMapper::toAggregate);
  }

  /// 查找任务的所有执行尝试，按 attemptNo 升序排列。
  ///
  /// @param taskId 任务 ID
  /// @return 所有尝试记录列表，可能为空
  @Override
  public List<TaskRun> findAll(Long taskId) {
    List<TaskRunEntity> entities =
        taskRunDao.findByTaskId(taskId).stream()
            .sorted((a, b) -> Integer.compare(a.getAttemptNo(), b.getAttemptNo()))
            .toList();
    if (log.isDebugEnabled()) {
      log.debug("query all TaskRun by taskId={}, found {} results", taskId, entities.size());
    }
    return entities.stream().map(taskRunJpaMapper::toAggregate).toList();
  }

  /// 获取任务的最新 attemptNo 用于生成下一次尝试编号。
  ///
  /// @param taskId 任务 ID
  /// @return 最大 attemptNo，如果没有执行记录则返回 0
  @Override
  public int getLatestAttemptNo(Long taskId) {
    Integer latest = taskRunDao.selectLatestAttemptNo(taskId);
    return latest != null ? latest : 0;
  }

  /// 根据 ID 查找执行记录。
  ///
  /// @param runId 执行记录 ID
  /// @return 执行记录（可选）
  @Override
  public Optional<TaskRun> findById(Long runId) {
    if (runId == null) {
      return Optional.empty();
    }
    return taskRunDao.findById(runId).map(taskRunJpaMapper::toAggregate);
  }

  /// 更新检查点并刷新心跳时间戳。
  ///
  /// @param runId 执行记录 ID
  /// @param checkpointJson 检查点 JSON 字符串
  /// @param now 当前时间
  /// @return 更新成功返回 true，否则返回 false
  @Override
  public boolean updateCheckpointAndHeartbeat(Long runId, String checkpointJson, Instant now) {
    int updated = taskRunDao.updateCheckpointAndHeartbeat(runId, checkpointJson, now);
    if (updated == 0) {
      log.warn("task run checkpoint update missed runId={}", runId);
    } else if (log.isDebugEnabled()) {
      log.debug("updated checkpoint and heartbeat for runId={}", runId);
    }
    return updated > 0;
  }

  /// 刷新心跳时间戳。
  ///
  /// @param runId 执行记录 ID
  /// @param now 当前时间
  /// @return 更新成功返回 true，否则返回 false
  @Override
  public boolean touchHeartbeat(Long runId, Instant now) {
    int updated = taskRunDao.touchHeartbeat(runId, now);
    if (updated == 0) {
      log.warn("task run heartbeat touch missed runId={}", runId);
    } else if (log.isDebugEnabled()) {
      log.debug("touched heartbeat for runId={}", runId);
    }
    return updated > 0;
  }

  /// 标记执行记录为失败。
  ///
  /// @param runId 执行记录 ID
  /// @param errorMessage 错误消息
  /// @param now 当前时间
  /// @return 更新成功返回 true，否则返回 false
  @Override
  public boolean markFailed(Long runId, String errorMessage, Instant now) {
    int updated = taskRunDao.markFailed(runId, errorMessage, now);
    if (updated == 0) {
      log.warn("task run markFailed missed runId={}", runId);
    }
    return updated > 0;
  }

  /// 检查任务是否有成功的执行记录。
  ///
  /// @param taskId 任务 ID
  /// @return 有成功记录返回 true，否则返回 false
  @Override
  public boolean hasSucceededRun(Long taskId) {
    if (taskId == null) {
      return false;
    }
    List<TaskRunEntity> runs = taskRunDao.findByTaskId(taskId);
    boolean hasSucceeded =
        runs.stream().anyMatch(r -> TaskRunStatus.SUCCEEDED.getCode().equals(r.getStatusCode()));
    if (log.isDebugEnabled()) {
      log.debug("check succeeded run for taskId={}, hasSucceeded={}", taskId, hasSucceeded);
    }
    return hasSucceeded;
  }
}
