package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.MetricSnapshotDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 外部计量指标快照Mapper
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface MetricSnapshotMapper extends BaseMapper<MetricSnapshotDO> {
    
}
