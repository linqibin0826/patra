package dev.linqibin.patra.ingest.infra.adapter.persistence;

import dev.linqibin.patra.ingest.domain.model.entity.TaskRunBatch;
import dev.linqibin.patra.ingest.domain.model.enums.BatchStatus;
import dev.linqibin.patra.ingest.domain.port.TaskRunBatchRepository;
import dev.linqibin.patra.ingest.infra.adapter.persistence.converter.mapper.TaskRunBatchJpaMapper;
import dev.linqibin.patra.ingest.infra.adapter.persistence.dao.TaskRunBatchDao;
import dev.linqibin.patra.ingest.infra.adapter.persistence.entity.TaskRunBatchEntity;
import dev.linqibin.starter.jpa.id.SnowflakeIdGenerator;
import jakarta.persistence.EntityManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 任务执行批次（TaskRunBatch）仓储实现，基于 JPA。
///
/// 职责：持久化批次统计和分页信息（pageNo/pageSize），用于增量采集期间的分块处理。
///
/// 注意：当前批量保存使用顺序写入；如果批次数量显著增长，考虑使用批量 SQL 或异步写入。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunBatchRepositoryAdapter implements TaskRunBatchRepository {

  /// TaskRunBatch JPA Repository
  private final TaskRunBatchDao taskRunBatchDao;

  /// 领域实体与 JPA 实体转换器
  private final TaskRunBatchJpaMapper taskRunBatchJpaMapper;

  /// JPA EntityManager，用于 merge 操作
  private final EntityManager entityManager;

  /// 保存单个任务执行批次并返回持久化后的实体。
  ///
  /// @param batch 批次实体
  /// @return 持久化后的批次实体（包含数据库生成的 ID）
  @Override
  public TaskRunBatch save(TaskRunBatch batch) {
    TaskRunBatchEntity entity = taskRunBatchJpaMapper.toEntity(batch);

    TaskRunBatchEntity saved;
    if (batch.getId() == null) {
      // 新增：预分配雪花 ID
      entity.setId(SnowflakeIdGenerator.getId());
      saved = taskRunBatchDao.save(entity);
    } else {
      // 更新：ValueObjectJpaEntity 无需乐观锁
      entity.setId(batch.getId());
      // 使用 merge 处理可能存在的托管实体冲突
      saved = entityManager.merge(entity);
    }

    return taskRunBatchJpaMapper.toAggregate(saved);
  }

  /// 批量保存任务执行批次。
  ///
  /// 分离新增和更新操作：
  /// - 新增：使用 JPA `saveAll()` 批量插入
  /// - 更新：逐条 merge（ValueObjectJpaEntity 无乐观锁）
  ///
  /// @param batches 批次实体集合
  @Override
  public void saveAll(List<TaskRunBatch> batches) {
    if (batches == null || batches.isEmpty()) {
      return;
    }

    // 分离新增和更新
    List<TaskRunBatchEntity> toInsert = new ArrayList<>();
    List<TaskRunBatch> toUpdate = new ArrayList<>();

    for (TaskRunBatch batch : batches) {
      if (batch.getId() == null) {
        TaskRunBatchEntity entity = taskRunBatchJpaMapper.toEntity(batch);
        entity.setId(SnowflakeIdGenerator.getId());
        toInsert.add(entity);
      } else {
        toUpdate.add(batch);
      }
    }

    // 批量插入新批次
    if (!toInsert.isEmpty()) {
      taskRunBatchDao.saveAll(toInsert);
      if (log.isDebugEnabled()) {
        log.debug("Batch inserted {} TaskRunBatch records", toInsert.size());
      }
    }

    // 逐条更新已有批次（保持乐观锁语义）
    for (TaskRunBatch batch : toUpdate) {
      save(batch);
    }
    if (!toUpdate.isEmpty() && log.isDebugEnabled()) {
      log.debug("Updated {} TaskRunBatch records", toUpdate.size());
    }
  }

  /// 查找特定任务执行尝试的所有批次。
  ///
  /// @param runId 任务执行尝试 ID
  /// @return 批次列表，可能为空
  @Override
  public List<TaskRunBatch> findByRunId(Long runId) {
    List<TaskRunBatchEntity> entities = taskRunBatchDao.findByRunId(runId);
    if (log.isDebugEnabled()) {
      log.debug("query TaskRunBatch by runId={}, found {} results", runId, entities.size());
    }
    return entities.stream().map(taskRunBatchJpaMapper::toAggregate).toList();
  }

  /// 查找给定执行的最后一个成功批次的 ID。
  ///
  /// 用于游标血缘跟踪，记录哪个批次触发了游标推进。
  ///
  /// @param runId 执行标识符
  /// @return 批次 ID（可选，按 ID 排序的最新 SUCCEEDED 批次）
  @Override
  public Optional<Long> findLastSucceededBatchId(Long runId) {
    List<TaskRunBatchEntity> batches = taskRunBatchDao.findByRunId(runId);

    Long batchId =
        batches.stream()
            .filter(b -> BatchStatus.SUCCEEDED.getCode().equals(b.getStatusCode()))
            .map(TaskRunBatchEntity::getId)
            .max(Long::compareTo)
            .orElse(null);

    if (log.isDebugEnabled()) {
      log.debug("query last succeeded batch for runId={}, batchId={}", runId, batchId);
    }

    return Optional.ofNullable(batchId);
  }
}
