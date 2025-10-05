package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only mapper for {@code reg_prov_expr_capability}.
 * Provides convenience queries to locate the effective capability slice for a field.
 *
 * @author linqibin
 * @since 0.1.0
 */

public interface RegProvExprCapabilityMapper extends BaseMapper<RegProvExprCapabilityDO> {

    /**
     * Fetches the most specific active capability slice for the requested field.
     *
     * @param provenanceId      provenance identifier
     * @param operationType     normalized operation type (ALL fallback supported)
     * @param fieldKey          canonical field key
     * @param now               evaluation timestamp
     * @return optional capability effective at {@code now}
     */
    Optional<RegProvExprCapabilityDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                   @Param("operationType") String operationType,
                                                   @Param("fieldKey") String fieldKey,
                                                   @Param("now") Instant now);

    /**
     * Lists all active capability slices for the provided operation scope,
     * collapsing source-level rows with the same {@code field_key}.
     *
     * @param provenanceId      provenance identifier
     * @param operationType     normalized operation type (ALL fallback supported)
     * @param now               evaluation timestamp
     * @return list of capabilities, one per field
     */
    List<RegProvExprCapabilityDO> selectActiveByTask(@Param("provenanceId") Long provenanceId,
                                                     @Param("operationType") String operationType,
                                                     @Param("now") Instant now);
}
