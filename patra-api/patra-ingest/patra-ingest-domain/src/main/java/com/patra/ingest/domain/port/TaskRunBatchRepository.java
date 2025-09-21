package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRunBatch;
import java.util.List;

public interface TaskRunBatchRepository {
    void saveAll(List<TaskRunBatch> batches);
    List<TaskRunBatch> findByRunId(Long runId);
}
