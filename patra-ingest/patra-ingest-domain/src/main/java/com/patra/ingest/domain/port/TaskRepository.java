package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.TaskAggregate;

import java.util.List;

/**
 * 任务聚合仓储端口定义。
 * <p>用于持久化任务聚合（包含计划、切片、运行配置等上下文），并提供按计划维度的查询能力以及排队任务数量统计，
 * 帮助应用层完成调度决策与容量管理。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface TaskRepository {

    /**
     * 保存或更新单个任务聚合。
     *
     * @param task 任务聚合，包含任务元数据、执行策略与初始状态
     * @return 持久化后的任务聚合，一般会补齐数据库主键
     */
    TaskAggregate save(TaskAggregate task);

    /**
     * 批量保存任务聚合，常用于计划切片生成后的一次性落库。
     *
     * @param tasks 任务聚合集合
     * @return 持久化后的任务聚集列表，顺序与入参一致
     */
    List<TaskAggregate> saveAll(List<TaskAggregate> tasks);

    /**
     * 按计划 ID 查询全部任务。
     *
     * @param planId 计划 ID
     * @return 归属该计划的任务列表
     */
    List<TaskAggregate> findByPlanId(Long planId);

    /**
     * 统计处于排队状态（状态码为 QUEUED）的任务数量。
     *
     * @param provenanceCode 来源编码，可为空表示不过滤
     * @param operationCode  操作编码，可为空表示不过滤
     * @return 满足条件的排队任务数量
     */
    long countQueuedTasks(String provenanceCode, String operationCode);
}
