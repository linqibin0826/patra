package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.JobDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 采集任务Mapper
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface JobMapper extends BaseMapper<JobDO> {
    
}
