package com.patra.ingest.infra.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.patra.ingest.domain.model.entity.TaskRunBatch;
import com.patra.ingest.domain.port.TaskRunBatchRepository;
import com.patra.ingest.infra.persistence.converter.TaskRunBatchConverter;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import com.patra.ingest.infra.persistence.mapper.TaskRunBatchMapper;
import java.util.List;
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
      if (log.isDebugEnabled()) {
        log.debug(
            "task run batch insert runId={} batchNo={} status={}",
            dto.getRunId(),
            dto.getBatchNo(),
            dto.getStatusCode());
      }
    } else {
      mapper.updateById(dto);
      if (log.isDebugEnabled()) {
        log.debug(
            "task run batch update id={} runId={} batchNo={} status={}",
            dto.getId(),
            dto.getRunId(),
            dto.getBatchNo(),
            dto.getStatusCode());
      }
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
        if (log.isDebugEnabled()) {
          log.debug(
              "task run batch insert runId={} size={} status={}",
              dto.getRunId(),
              dto.getRecordCount(),
              dto.getStatusCode());
        }
      } else {
        mapper.updateById(dto);
        if (log.isDebugEnabled()) {
          log.debug(
              "task run batch update id={} runId={} size={} status={}",
              dto.getId(),
              dto.getRunId(),
              dto.getRecordCount(),
              dto.getStatusCode());
        }
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
    return mapper.selectList(new QueryWrapper<TaskRunBatchDO>().eq("run_id", runId)).stream()
        .map(converter::toDomain)
        .collect(Collectors.toList());
  }
}
