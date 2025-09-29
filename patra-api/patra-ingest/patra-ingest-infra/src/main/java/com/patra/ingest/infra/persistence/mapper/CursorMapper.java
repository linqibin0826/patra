package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 游标（Cursor）表 Mapper。
 * <p>
 * 语义：存放外部数据源增量同步位置（offset / watermark），支撑幂等与断点续传。
 * 后续如需按来源+操作维度查询，请新增方法并保持命名清晰（findByProvenanceAndOperation）。
 * </p>
 */
@Mapper
public interface CursorMapper extends BaseMapper<CursorDO> {
}
