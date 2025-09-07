package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.RunBatchDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/**
 * 运行批次Mapper接口
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface RunBatchMapper extends BaseMapper<RunBatchDO> {

}
