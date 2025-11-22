package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRun;
import com.patra.ingest.domain.model.enums.TaskRunStatus;
import com.patra.ingest.domain.port.TaskRunRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 任务执行记录（TaskRun）仓储实现,基于 MyBatis-Plus。
///
/// 职责:
///
/// - 插入和更新任务执行记录,包括统计数据和检查点 JSON 字段
///   - 查询任务的最新执行尝试(按 attempt_no DESC 排序, limit 1)
///   - 查询所有执行尝试,用于审计和故障排查
///   - 获取最新 attemptNo 用于生成下一次尝试编号
///
/// 设计:
///
/// - ID 为 null 时插入,否则更新
///   - 保存后再次 selectById 以获取数据库生成的字段(如后续添加的乐观锁 version)
///
/// 日志策略: DEBUG 级别记录 insert/update 及关键字段;查询操作不记录日志。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunRepositoryMpImpl implements TaskRunRepository {

  private final TaskRunMapper mapper;
  private final TaskRunConverter converter;

  /// Saves a task run record.
  ///
  /// @param run task run entity
  /// @return persisted task run with database-generated fields
  @Override
  public TaskRun save(TaskRun run) {
    TaskRunDO dto = converter.toDO(run);
    if (dto.getId() == null) {
      mapper.insert(dto);
    } else {
      mapper.updateById(dto);
    }
    // Return re-mapped latest database state including generated ID
    TaskRunDO persisted = mapper.selectById(dto.getId());
    return converter.toDomain(persisted);
  }

  /// Finds the latest task run attempt by task ID.
  ///
  /// @param taskId task ID
  /// @return Optional containing latest run, empty if task has not been run yet
  @Override
  public Optional<TaskRun> findLatest(Long taskId) {
    TaskRunDO one =
        mapper.selectOne(
            new LambdaQueryWrapper<TaskRunDO>()
                .eq(TaskRunDO::getTaskId, taskId)
                .orderByDesc(TaskRunDO::getAttemptNo)
                .last("limit 1"));
    if (log.isDebugEnabled()) {
      log.debug(
          "query latest TaskRun by taskId={}, found={}",
          taskId,
          one != null ? "attemptNo=" + one.getAttemptNo() : "none");
    }
    return Optional.ofNullable(one).map(converter::toDomain);
  }

  /// Finds all task run attempts ordered by attemptNo ascending.
  ///
  /// @param taskId task ID
  /// @return list of all attempts, may be empty
  @Override
  public List<TaskRun> findAll(Long taskId) {
    List<TaskRunDO> entities =
        mapper.selectList(
            new LambdaQueryWrapper<TaskRunDO>()
                .eq(TaskRunDO::getTaskId, taskId)
                .orderByAsc(TaskRunDO::getAttemptNo));
    if (log.isDebugEnabled()) {
      log.debug("query all TaskRun by taskId={}, found {} results", taskId, entities.size());
    }
    return entities.stream().map(converter::toDomain).collect(Collectors.toList());
  }

  /// Retrieves the latest attemptNo for a task to generate next attempt number.
  ///
  /// @param taskId task ID
  /// @return maximum attemptNo, or 0 if no run records exist
  @Override
  public int getLatestAttemptNo(Long taskId) {
    return mapper.selectLatestAttemptNo(taskId);
  }

  @Override
  public Optional<TaskRun> findById(Long runId) {
    if (runId == null) {
      return Optional.empty();
    }
    TaskRunDO entity = mapper.selectById(runId);
    return Optional.ofNullable(entity).map(converter::toDomain);
  }

  @Override
  public boolean updateCheckpointAndHeartbeat(Long runId, String checkpointJson, Instant now) {
    int updated = mapper.updateCheckpointAndHeartbeat(runId, checkpointJson, now);
    if (updated == 0) {
      log.warn("task run checkpoint update missed runId={}", runId);
    } else if (log.isDebugEnabled()) {
      log.debug("updated checkpoint and heartbeat for runId={}", runId);
    }
    return updated > 0;
  }

  @Override
  public boolean touchHeartbeat(Long runId, Instant now) {
    int updated = mapper.touchHeartbeat(runId, now);
    if (updated == 0) {
      log.warn("task run heartbeat touch missed runId={}", runId);
    } else if (log.isDebugEnabled()) {
      log.debug("touched heartbeat for runId={}", runId);
    }
    return updated > 0;
  }

  @Override
  public boolean markFailed(Long runId, String errorMessage, Instant now) {
    int updated = mapper.markFailed(runId, errorMessage, now);
    if (updated == 0) {
      log.warn("task run markFailed missed runId={}", runId);
    }
    return updated > 0;
  }

  @Override
  public boolean hasSucceededRun(Long taskId) {
    if (taskId == null) {
      return false;
    }
    Long count =
        mapper.selectCount(
            new LambdaQueryWrapper<TaskRunDO>()
                .eq(TaskRunDO::getTaskId, taskId)
                .eq(TaskRunDO::getStatusCode, TaskRunStatus.SUCCEEDED.getCode()));
    boolean hasSucceeded = count != null && count > 0;
    if (log.isDebugEnabled()) {
      log.debug("check succeeded run for taskId={}, hasSucceeded={}", taskId, hasSucceeded);
    }
    return hasSucceeded;
  }
}
