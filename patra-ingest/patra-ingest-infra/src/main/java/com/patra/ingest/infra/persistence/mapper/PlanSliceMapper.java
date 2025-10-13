package com.patra.ingest.infra.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.ingest.infra.persistence.entity.PlanSliceDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * Mapper for PlanSlice table.
 *
 * <p>Notes:
 *
 * <ul>
 *   <li>Extends {@link BaseMapper} and provides basic CRUD.
 *   <li>Add custom queries (e.g., by window/status) as needed with proper Javadoc.
 *   <li>Data-access only; do not embed domain logic. Let repository layer coordinate complex
 *       operations.
 * </ul>
 */
@Mapper
public interface PlanSliceMapper extends BaseMapper<PlanSliceDO> {
  // Placeholder: add methods and method-level docs when custom SQL is needed.
}
