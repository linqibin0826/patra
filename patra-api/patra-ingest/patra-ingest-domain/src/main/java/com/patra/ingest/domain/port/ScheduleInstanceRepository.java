package com.patra.ingest.domain.port;

import com.patra.ingest.domain.model.aggregate.ScheduleInstance;
import com.patra.ingest.domain.model.enums.SchedulerSource;

import java.util.Optional;

/**
 * 调度实例聚合的仓储端口。
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface ScheduleInstanceRepository {

    /**
     * 按ID查询。
     */
    Optional<ScheduleInstance> findById(Long id);

    /**
     * 按调度器来源+jobId+logId 唯一定位一次触发。
     */
    Optional<ScheduleInstance> findBySchedulerTuple(SchedulerSource scheduler, String schedulerJobId, String schedulerLogId);

    /**
     * 保存（新建或更新）。
     */
    ScheduleInstance save(ScheduleInstance aggregate);
}

