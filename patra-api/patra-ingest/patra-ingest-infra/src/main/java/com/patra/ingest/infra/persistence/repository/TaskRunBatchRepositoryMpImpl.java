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
 * MyBatis-Plus implementation of TaskRunBatchRepository.
 *
 * <p>Responsibilities: Persisting batch statistics and pagination information (pageNo/pageSize) for
 * chunked processing during incremental collection.
 *
 * <p>Note: Current batch save uses sequential writes; if batch volume grows significantly, consider
 * batch SQL or async writes.
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class TaskRunBatchRepositoryMpImpl implements TaskRunBatchRepository {

  private final TaskRunBatchMapper mapper;
  private final TaskRunBatchConverter converter;

  /**
   * Saves a single task run batch by inserting or updating.
   *
   * @param batch batch entity
   */
  @Override
  public void save(TaskRunBatch batch) {
    TaskRunBatchDO dto = converter.toDO(batch);
    if (dto.getId() == null) {
      mapper.insert(dto);
    } else {
      mapper.updateById(dto);
    }
  }

  /**
   * Batch saves task run batches by inserting or updating.
   *
   * @param batches collection of batch entities
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
   * Finds all batches for a specific task run attempt.
   *
   * @param runId task run attempt ID
   * @return list of batches, may be empty
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
   * Finds the batch ID of the last succeeded batch for a given run.
   *
   * <p>Used for cursor lineage tracking to record which batch triggered cursor advancement.
   *
   * @param runId run identifier
   * @return optional batch ID (latest SUCCEEDED batch by ID order)
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
