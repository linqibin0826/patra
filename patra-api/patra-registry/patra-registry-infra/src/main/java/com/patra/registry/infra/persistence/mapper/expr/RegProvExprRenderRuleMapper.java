package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only mapper for {@code reg_prov_expr_render_rule}.
 * Supplies helpers to locate the active render rule for expression atoms.
 *
 * @author linqibin
 * @since 0.1.0
 */

public interface RegProvExprRenderRuleMapper extends BaseMapper<RegProvExprRenderRuleDO> {

    /**
     * Retrieves the most specific active render rule matching the supplied dimension.
     *
     * @param provenanceId      provenance identifier
     * @param operationType     normalized operation type (ALL fallback supported)
     * @param fieldKey          canonical field key
     * @param opCode            expression operator code
     * @param matchTypeKey      normalized match type key
     * @param negatedKey        normalized negation key
     * @param valueTypeKey      normalized value type key
     * @param emitTypeCode      emission type (QUERY/PARAMS)
     * @param now               evaluation timestamp
     * @return optional render rule effective at {@code now}
     */
    Optional<RegProvExprRenderRuleDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                   @Param("operationType") String operationType,
                                                   @Param("fieldKey") String fieldKey,
                                                   @Param("opCode") String opCode,
                                                   @Param("matchTypeKey") String matchTypeKey,
                                                   @Param("negatedKey") String negatedKey,
                                                   @Param("valueTypeKey") String valueTypeKey,
                                                   @Param("emitTypeCode") String emitTypeCode,
                                                   @Param("now") Instant now);

    /**
     * Lists active render rules for the provided provenance/operation scope,
     * collapsing source-level fallbacks per rule signature.
     *
     * @param provenanceId      provenance identifier
     * @param operationType     normalized operation type
     * @param now               evaluation timestamp
     * @return list of render rules, one per unique rule signature
     */
    List<RegProvExprRenderRuleDO> selectActiveByTask(@Param("provenanceId") Long provenanceId,
                                                     @Param("operationType") String operationType,
                                                     @Param("now") Instant now);
}
