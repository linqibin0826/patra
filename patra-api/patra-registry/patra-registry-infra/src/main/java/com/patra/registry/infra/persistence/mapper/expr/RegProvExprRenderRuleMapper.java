package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_expr_render_rule} 表的只读 Mapper。
 */
@Mapper
public interface RegProvExprRenderRuleMapper extends BaseMapper<RegProvExprRenderRuleDO> {

    /** 查询指定维度当前生效的渲染规则。 */
    @Select("""
            SELECT *
            FROM reg_prov_expr_render_rule
            WHERE deleted = 0
              AND lifecycle_status_code = 'ACTIVE'
              AND provenance_id = #{provenanceId}
              AND scope_code = #{scopeCode}
              AND task_type_key = #{taskTypeKey}
              AND field_key = #{fieldKey}
              AND op_code = #{opCode}
              AND match_type_key = #{matchTypeKey}
              AND negated_key = #{negatedKey}
              AND value_type_key = #{valueTypeKey}
              AND emit_type_code = #{emitTypeCode}
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            ORDER BY effective_from DESC, id DESC
            LIMIT 1
            """)
    Optional<RegProvExprRenderRuleDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                   @Param("scopeCode") String scopeCode,
                                                   @Param("taskTypeKey") String taskTypeKey,
                                                   @Param("fieldKey") String fieldKey,
                                                   @Param("opCode") String opCode,
                                                   @Param("matchTypeKey") String matchTypeKey,
                                                   @Param("negatedKey") String negatedKey,
                                                   @Param("valueTypeKey") String valueTypeKey,
                                                   @Param("emitTypeCode") String emitTypeCode,
                                                   @Param("now") Instant now);
}
