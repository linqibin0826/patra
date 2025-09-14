package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.ScheduleInstanceDO;

/**
 * Mapper · ing_schedule_instance
 * <p>
 * 使用 MyBatis-Plus 提供的标准 CRUD 方法；严禁添加自定义方法。
 * 任何复杂查询请在 Repository 层组合 baseMapper 及 Wrapper 完成。
 * </p>
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface IngScheduleInstanceMapper extends BaseMapper<ScheduleInstanceDO> {
    // 严禁添加自定义方法，所有操作通过 MyBatis-Plus 提供的标准方法实现
}
