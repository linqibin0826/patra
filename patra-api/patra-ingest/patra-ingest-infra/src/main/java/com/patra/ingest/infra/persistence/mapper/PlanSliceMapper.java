package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 计划切片Mapper
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface PlanSliceMapper extends BaseMapper<PlanSliceDO> {
    
}
