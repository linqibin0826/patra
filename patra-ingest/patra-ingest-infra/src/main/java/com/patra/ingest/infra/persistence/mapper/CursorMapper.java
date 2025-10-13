package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.CursorDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for cursor table.
 *
 * <p>Stores incremental synchronization positions from external data sources (offset/watermark),
 * supporting idempotency and resume-from-checkpoint flows. If queries by provenance+operation are
 * needed later, add methods with clear names (e.g., findByProvenanceAndOperation).
 */
@Mapper
public interface CursorMapper extends BaseMapper<CursorDO> {}
