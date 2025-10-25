package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRun;
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

/**
 * MyBatis-Plus implementation of TaskRunRepository for task run attempts.
 *
 * <p>Responsibilities:
 *
 * <ul>
 *   <li>Insert and update TaskRun including statistics and checkpoint JSON fields.
 *   <li>Query latest attempt by task ID (ordered by attempt_no DESC, limit 1).
 *   <li>Query all attempts for audit and troubleshooting.
 *   <li>Retrieve latest attemptNo for generating next attempt number.
 * </ul>
 *
 * <p>Design:
 *
 * <ul>
 *   <li>If ID is null: insert; otherwise: update.
 *   <li>After save, selectById again to retrieve database-generated fields (e.g., optimistic lock
 *       version if added later).
 * </ul>
 *
 * <p>Logging strategy: DEBUG for insert/update with key fields; no query logging.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunRepositoryMpImpl implements TaskRunRepository {

  private final TaskRunMapper mapper;
  private final TaskRunConverter converter;

  /**
   * Saves a task run record.
   *
   * @param run task run entity
   * @return persisted task run with database-generated fields
   */
  @Override
  public TaskRun save(TaskRun run) {
    TaskRunDO dto = converter.toDO(run);
    if (dto.getId() == null) {
      mapper.insert(dto);
      if (log.isDebugEnabled()) {
        log.debug("task run insert taskId={} attemptNo={}", dto.getTaskId(), dto.getAttemptNo());
      }
    } else {
      mapper.updateById(dto);
      if (log.isDebugEnabled()) {
        log.debug(
            "task run update id={} attemptNo={} status={}",
            dto.getId(),
            dto.getAttemptNo(),
            dto.getStatusCode());
      }
    }
    // Return re-mapped latest database state including generated ID
    TaskRunDO persisted = mapper.selectById(dto.getId());
    return converter.toDomain(persisted);
  }

  /**
   * Finds the latest task run attempt by task ID.
   *
   * @param taskId task ID
   * @return Optional containing latest run, empty if task has not been run yet
   */
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

  /**
   * Finds all task run attempts ordered by attemptNo ascending.
   *
   * @param taskId task ID
   * @return list of all attempts, may be empty
   */
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

  /**
   * Retrieves the latest attemptNo for a task to generate next attempt number.
   *
   * @param taskId task ID
   * @return maximum attemptNo, or 0 if no run records exist
   */
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
                .eq(TaskRunDO::getStatusCode, "SUCCEEDED"));
    // TODO: Replace hardcoded status with enum shared between DO and domain
    boolean hasSucceeded = count != null && count > 0;
    if (log.isDebugEnabled()) {
      log.debug("check succeeded run for taskId={}, hasSucceeded={}", taskId, hasSucceeded);
    }
    return hasSucceeded;
  }
}
