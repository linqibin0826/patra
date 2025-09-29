package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游标事件（CursorEvent）表 Mapper。
 * <p>
 * 用途：记录游标推进过程中的事件轨迹（检测 / 回退 / 推进），用于审计与问题排查。
 * 仅做单表操作，不在此层拼接复杂历史统计逻辑。
 * </p>
 */
@Mapper
public interface CursorEventMapper extends BaseMapper<CursorEventDO> {
}
