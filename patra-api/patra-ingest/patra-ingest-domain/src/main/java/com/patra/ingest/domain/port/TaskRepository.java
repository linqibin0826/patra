package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

import java.util.List;

/**
 * 任务仓储端口定义。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRepository {
    /** 保存任务 */
    TaskAggregate save(TaskAggregate task);

    /** 批量保存任务 */
    List<TaskAggregate> saveAll(List<TaskAggregate> tasks);

    /** 根据计划 ID 查询任务 */
    List<TaskAggregate> findByPlanId(Long planId);

    /**
     * 统计排队中的任务数量（status_code=QUEUED），可按来源/操作可选过滤。
     */
    long countQueuedTasks(String provenanceCode, String operationCode);
}
