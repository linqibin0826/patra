package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRun;
import java.util.List;
import java.util.Optional;

public interface TaskRunRepository {
    TaskRun save(TaskRun run);
    Optional<TaskRun> findLatest(Long taskId); // 按 attemptNo DESC 取 1
    List<TaskRun> findAll(Long taskId);
}
