package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprRenderRuleDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code reg_prov_expr_render_rule} 表的只读 Mapper。
 */

public interface RegProvExprRenderRuleMapper extends BaseMapper<RegProvExprRenderRuleDO> {

    /** 查询指定维度当前生效的渲染规则。 */
    @Select("""
            SELECT *
            FROM reg_prov_expr_render_rule
            WHERE deleted = 0
              AND lifecycle_status_code = 'ACTIVE'
              AND provenance_id = #{provenanceId}
              AND task_type_key IN (#{taskTypeKey}, 'ALL')
              AND field_key = #{fieldKey}
              AND op_code = #{opCode}
              AND match_type_key = #{matchTypeKey}
              AND negated_key = #{negatedKey}
              AND value_type_key = #{valueTypeKey}
              AND emit_type_code = #{emitTypeCode}
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            ORDER BY
              CASE WHEN task_type_key = #{taskTypeKey} THEN 1 ELSE 2 END,
              effective_from DESC,
              id DESC
            LIMIT 1
            """)
    Optional<RegProvExprRenderRuleDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                   @Param("taskTypeKey") String taskTypeKey,
                                                   @Param("fieldKey") String fieldKey,
                                                   @Param("opCode") String opCode,
                                                   @Param("matchTypeKey") String matchTypeKey,
                                                   @Param("negatedKey") String negatedKey,
                                                   @Param("valueTypeKey") String valueTypeKey,
                                                   @Param("emitTypeCode") String emitTypeCode,
                                                   @Param("now") Instant now);

    /** 查询指定任务维度（含 ALL 回退）下全部当前生效的渲染规则。 */
    @Select("""
            SELECT *
            FROM (
                SELECT r.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY r.field_key, r.op_code, r.match_type_key, r.negated_key, r.value_type_key, r.emit_type_code
                           ORDER BY CASE WHEN r.task_type_key = #{taskTypeKey} THEN 1 ELSE 2 END,
                                    r.effective_from DESC,
                                    r.id DESC
                       ) AS rn
                FROM reg_prov_expr_render_rule r
                WHERE r.deleted = 0
                  AND r.lifecycle_status_code = 'ACTIVE'
                  AND r.provenance_id = #{provenanceId}
                  AND r.task_type_key IN (#{taskTypeKey}, 'ALL')
                  AND r.effective_from <= #{now}
                  AND (r.effective_to IS NULL OR r.effective_to > #{now})
            ) t
            WHERE t.rn = 1
            ORDER BY t.field_key, t.op_code, t.match_type_key, t.negated_key, t.value_type_key, t.emit_type_code
            """)
    List<RegProvExprRenderRuleDO> selectActiveByTask(@Param("provenanceId") Long provenanceId,
                                                     @Param("taskTypeKey") String taskTypeKey,
                                                     @Param("now") Instant now);
}
