package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRunBatch;

import java.util.List;
import java.util.Optional;

/**
 * 运行批次仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunBatchRepository {

    Optional<TaskRunBatch> findById(Long id);

    List<TaskRunBatch> findByRunId(Long runId);

    Optional<TaskRunBatch> findByRunIdAndBatchNo(Long runId, Integer batchNo);

    Optional<TaskRunBatch> findByRunIdAndBeforeToken(Long runId, String beforeToken);

    Optional<TaskRunBatch> findByIdempotentKey(String idempotentKey);

    TaskRunBatch save(TaskRunBatch batch);
}

