package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorHistoryDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游标推进历史Mapper
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper
public interface CursorHistoryMapper extends BaseMapper<CursorHistoryDO> {
    
}
