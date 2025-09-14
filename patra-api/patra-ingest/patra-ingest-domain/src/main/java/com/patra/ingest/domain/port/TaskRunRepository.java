package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.entity.TaskRun;

import java.util.List;
import java.util.Optional;

/**
 * 任务运行（attempt）仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRunRepository {

    Optional<TaskRun> findById(Long id);

    List<TaskRun> findByTaskId(Long taskId);

    Optional<TaskRun> findByTaskIdAndAttemptNo(Long taskId, Integer attemptNo);

    TaskRun save(TaskRun run);
}

