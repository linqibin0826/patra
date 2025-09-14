package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.Task;

import java.util.List;
import java.util.Optional;

/**
 * 任务仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRepository {

    Optional<Task> findById(Long id);

    Optional<Task> findByIdempotentKey(String idempotentKey);

    List<Task> findBySliceId(Long sliceId);

    Task save(Task task);
}

