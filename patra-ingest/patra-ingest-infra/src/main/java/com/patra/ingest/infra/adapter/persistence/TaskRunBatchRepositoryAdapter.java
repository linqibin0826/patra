package com.patra.ingest.infra.adapter.persistence;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.exception.TaskPersistenceException;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunBatchConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunBatchMapper;
import com.patra.starter.mybatis.batch.BatchInsertHelper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 任务执行批次（TaskRunBatch）仓储实现,基于 MyBatis-Plus。
///
/// 职责: 持久化批次统计和分页信息(pageNo/pageSize),用于增量采集期间的分块处理。
///
/// 注意: 当前批量保存使用顺序写入;如果批次数量显著增长,考虑使用批量 SQL 或异步写入。
///
/// @author linqibin
/// @since 0.1.0
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunBatchRepositoryAdapter implements TaskRunBatchRepository {

  private final TaskRunBatchMapper mapper;
  private final TaskRunBatchConverter converter;

  /// 保存单个任务执行批次并返回持久化后的实体。
  ///
  /// @param batch 批次实体
  /// @return 持久化后的批次实体（包含数据库生成的 ID）
  @Override
  public TaskRunBatch save(TaskRunBatch batch) {
    TaskRunBatchDO dto = converter.toDO(batch);
    if (dto.getId() == null) {
      mapper.insert(dto); // MyBatis-Plus 自动回填 dto.id
    } else {
      mapper.updateById(dto);
    }
    return converter.toDomain(dto); // 返回带 ID 的领域实体
  }

  /// 批量保存任务执行批次。
  ///
  /// 分离新增和更新操作：
  /// - 新增: 使用 `insertBatchSomeColumn` 批量插入
  /// - 更新: 逐条更新（保持乐观锁语义）
  ///
  /// @param batches 批次实体集合
  @Override
  public void saveAll(List<TaskRunBatch> batches) {
    if (batches == null || batches.isEmpty()) {
      return;
    }

    // 分离新增和更新
    List<TaskRunBatchDO> toInsert = new ArrayList<>();
    List<TaskRunBatchDO> toUpdate = new ArrayList<>();

    for (TaskRunBatch batch : batches) {
      TaskRunBatchDO dto = converter.toDO(batch);
      if (dto.getId() == null) {
        toInsert.add(dto);
      } else {
        toUpdate.add(dto);
      }
    }

    // 批量插入新批次
    if (!toInsert.isEmpty()) {
      var result = BatchInsertHelper.batchInsert(toInsert, mapper::insertBatchSomeColumn);
      if (result.hasErrors()) {
        log.error(
            "TaskRunBatch 批量插入部分失败：成功 {} / 总计 {}", result.successCount(), result.totalCount());
        throw new TaskPersistenceException(
            "TaskRunBatch 批量插入部分失败，失败批次数: " + result.errors().size());
      }
      if (log.isDebugEnabled()) {
        log.debug("Batch inserted {} TaskRunBatch records", toInsert.size());
      }
    }

    // 逐条更新已有批次（保持乐观锁语义）
    for (TaskRunBatchDO dto : toUpdate) {
      mapper.updateById(dto);
    }
    if (!toUpdate.isEmpty() && log.isDebugEnabled()) {
      log.debug("Updated {} TaskRunBatch records", toUpdate.size());
    }
  }

  /// 查找特定任务执行尝试的所有批次。
  ///
  /// @param runId 任务执行尝试 ID
  /// @return 批次列表,可能为空
  @Override
  public List<TaskRunBatch> findByRunId(Long runId) {
    List<TaskRunBatchDO> entities =
        mapper.selectList(
            new LambdaQueryWrapper<TaskRunBatchDO>().eq(TaskRunBatchDO::getRunId, runId));
    if (log.isDebugEnabled()) {
      log.debug("query TaskRunBatch by runId={}, found {} results", runId, entities.size());
    }
    return entities.stream().map(converter::toDomain).collect(Collectors.toList());
  }

  /// 查找给定执行的最后一个成功批次的 ID。
  ///
  /// 用于游标血缘跟踪,记录哪个批次触发了游标推进。
  ///
  /// @param runId 执行标识符
  /// @return 批次 ID(可选,按 ID 排序的最新 SUCCEEDED 批次)
  @Override
  public Optional<Long> findLastSucceededBatchId(Long runId) {
    LambdaQueryWrapper<TaskRunBatchDO> wrapper =
        new LambdaQueryWrapper<TaskRunBatchDO>()
            .eq(TaskRunBatchDO::getRunId, runId)
            .eq(TaskRunBatchDO::getStatusCode, BatchStatus.SUCCEEDED.getCode())
            .orderByDesc(TaskRunBatchDO::getId)
            .last("LIMIT 1");

    TaskRunBatchDO batch = mapper.selectOne(wrapper);
    Long batchId = batch != null ? batch.getId() : null;

    if (log.isDebugEnabled()) {
      log.debug("query last succeeded batch for runId={}, batchId={}", runId, batchId);
    }

    return Optional.ofNullable(batchId);
  }
}
