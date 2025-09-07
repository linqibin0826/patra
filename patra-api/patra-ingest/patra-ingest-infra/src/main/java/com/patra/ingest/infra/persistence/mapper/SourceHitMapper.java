package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.SourceHitDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 数据源命中Mapper接口
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface SourceHitMapper extends BaseMapper<SourceHitDO> {
    

}
