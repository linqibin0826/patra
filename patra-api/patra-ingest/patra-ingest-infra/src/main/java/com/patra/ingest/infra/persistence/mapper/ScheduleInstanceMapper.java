package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for schedule instance (ScheduleInstance) table.
 * <p>
 * Semantics: each scheduling trigger (manual/cron/API) creates an instance that links
 * the full chain of Plan/Task/Slice for this dispatch. CRUD only; for future time+scheduler
 * range queries, add dedicated methods (e.g., findBySchedulerAndTimeRange).
 * </p>
 */
@Mapper
public interface ScheduleInstanceMapper extends BaseMapper<ScheduleInstanceDO> {
}
