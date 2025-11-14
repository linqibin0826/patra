package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.model.enums.BatchStatus;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunBatchConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunBatchMapper;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/**
 * 任务执行批次（TaskRunBatch）仓储实现,基于 MyBatis-Plus。
 *
 * <p>职责: 持久化批次统计和分页信息(pageNo/pageSize),用于增量采集期间的分块处理。
 *
 * <p>注意: 当前批量保存使用顺序写入;如果批次数量显著增长,考虑使用批量 SQL 或异步写入。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunBatchRepositoryMpImpl implements TaskRunBatchRepository {

  private final TaskRunBatchMapper mapper;
  private final TaskRunBatchConverter converter;

  /**
   * 保存单个任务执行批次并返回持久化后的实体。
   *
   * @param batch 批次实体
   * @return 持久化后的批次实体（包含数据库生成的 ID）
   */
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

  /**
   * 批量保存任务执行批次。
   *
   * @param batches 批次实体集合
   */
  @Override
  public void saveAll(List<TaskRunBatch> batches) {
    for (TaskRunBatch batch : batches) {
      TaskRunBatchDO dto = converter.toDO(batch);
      if (dto.getId() == null) {
        mapper.insert(dto);
      } else {
        mapper.updateById(dto);
      }
    }
  }

  /**
   * 查找特定任务执行尝试的所有批次。
   *
   * @param runId 任务执行尝试 ID
   * @return 批次列表,可能为空
   */
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

  /**
   * 查找给定执行的最后一个成功批次的 ID。
   *
   * <p>用于游标血缘跟踪,记录哪个批次触发了游标推进。
   *
   * @param runId 执行标识符
   * @return 批次 ID(可选,按 ID 排序的最新 SUCCEEDED 批次)
   */
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
