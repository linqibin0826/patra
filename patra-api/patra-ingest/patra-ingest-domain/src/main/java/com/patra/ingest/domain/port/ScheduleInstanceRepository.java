package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.ScheduleInstanceAggregate;

/**
 * 调度实例仓储端口。
 * <p>用于记录每次调度触发的上下文快照，支撑后续计划重放、审计追踪与关联任务。</p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ScheduleInstanceRepository {

    /**
     * 保存或更新调度实例聚合。
     *
     * @param instance 调度实例聚合，包含调度器、触发时间与参数
     * @return 持久化后的调度实例
     */
    ScheduleInstanceAggregate saveOrUpdateInstance(ScheduleInstanceAggregate instance);
}
