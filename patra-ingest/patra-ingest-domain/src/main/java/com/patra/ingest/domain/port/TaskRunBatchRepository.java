package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRunBatch;
import java.util.List;

/**
 * Repository port for task execution batches.
 *
 * <p>Tasks can be split into batches (pagination/tokenization) during execution; this port persists
 * and queries batch-level state.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunBatchRepository {

  /**
   * Persist a single task run batch.
   *
   * @param batch batch entity
   */
  void save(TaskRunBatch batch);

  /**
   * Persist multiple task run batches at once.
   *
   * @param batches batch entities including status and metrics
   */
  void saveAll(List<TaskRunBatch> batches);

  /**
   * Retrieve batches by run identifier.
   *
   * @param runId run identifier
   * @return batches belonging to the run
   */
  List<TaskRunBatch> findByRunId(Long runId);
}
