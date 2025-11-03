package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 调度实例 Mapper 接口 — 对调度实例表的数据访问操作。
 *
 * <p>语义: 每次调度触发(手动/定时/API)创建一个实例,链接此次调度的完整链条(Plan/Task/Slice)。仅提供CRUD;未来如需时间+调度器范围查询,请添加专用方法(如
 * findBySchedulerAndTimeRange)。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface ScheduleInstanceMapper extends BaseMapper<ScheduleInstanceDO> {}
