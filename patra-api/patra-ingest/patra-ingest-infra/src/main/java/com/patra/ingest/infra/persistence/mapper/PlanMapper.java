package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集计划Mapper
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface PlanMapper extends BaseMapper<PlanDO> {
    
}
