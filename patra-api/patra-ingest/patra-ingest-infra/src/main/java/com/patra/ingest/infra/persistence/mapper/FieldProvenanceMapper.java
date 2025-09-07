package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.FieldProvenanceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 字段级溯源Mapper
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface FieldProvenanceMapper extends BaseMapper<FieldProvenanceDO> {
    
}
