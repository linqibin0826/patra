package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorEventDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for cursor events (CursorEvent).
 * <p>
 * Purpose: record the timeline of cursor advancement events (detect/rollback/advance) for auditing and
 * troubleshooting. Only single-table operations are performed here; do not stitch complex history
 * aggregation logic at the mapper layer.
 * </p>
 */
@Mapper
public interface CursorEventMapper extends BaseMapper<CursorEventDO> {
}
