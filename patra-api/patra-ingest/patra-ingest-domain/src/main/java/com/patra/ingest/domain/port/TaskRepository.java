package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

import java.util.List;

public interface TaskRepository {
    TaskAggregate save(TaskAggregate task);

    List<TaskAggregate> saveAll(List<TaskAggregate> tasks);

    List<TaskAggregate> findByPlanId(Long planId);

    /**
     * 统计排队中的任务数量（status_code=QUEUED），可按来源/操作可选过滤。
     */
    long countQueuedTasks(String provenanceCode, String operationCode);
}
