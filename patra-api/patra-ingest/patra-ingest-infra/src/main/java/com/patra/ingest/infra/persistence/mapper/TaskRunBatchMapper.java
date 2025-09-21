package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.TaskRunBatchDO;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface TaskRunBatchMapper extends BaseMapper<TaskRunBatchDO> {
}
