package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度实例（ScheduleInstance）表 Mapper。
 * <p>
 * 语义：一次调度触发（人工 / 定时 / API）形成一个实例，用于串联本次下发 Plan / Task / Slice 的全链路追踪。
 * 说明：仅提供单表 CRUD；若未来需要按照时间+调度器分页检索，请新增方法（如 findBySchedulerAndTimeRange）。
 * </p>
 */
@Mapper
public interface ScheduleInstanceMapper extends BaseMapper<ScheduleInstanceDO> {
}
