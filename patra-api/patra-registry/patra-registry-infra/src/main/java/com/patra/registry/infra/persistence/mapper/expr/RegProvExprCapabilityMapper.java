package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code reg_prov_expr_capability} 表的只读 Mapper。
 */

public interface RegProvExprCapabilityMapper extends BaseMapper<RegProvExprCapabilityDO> {

    /** 查询指定维度当前生效的字段能力。 */
    @Select("""
            SELECT *
            FROM reg_prov_expr_capability
            WHERE deleted = 0
              AND lifecycle_status_code = 'ACTIVE'
              AND provenance_id = #{provenanceId}
              AND task_type_key IN (#{taskTypeKey}, 'ALL')
              AND field_key = #{fieldKey}
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            ORDER BY
              CASE WHEN task_type_key = #{taskTypeKey} THEN 1 ELSE 2 END,
              effective_from DESC,
              id DESC
            LIMIT 1
            """)
    Optional<RegProvExprCapabilityDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                   @Param("taskTypeKey") String taskTypeKey,
                                                   @Param("fieldKey") String fieldKey,
                                                   @Param("now") Instant now);

    /** 查询指定任务维度（含 ALL 回退）下全部当前生效的字段能力。 */
    @Select("""
            SELECT *
            FROM (
                SELECT c.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY c.field_key
                           ORDER BY CASE WHEN c.task_type_key = #{taskTypeKey} THEN 1 ELSE 2 END,
                                    c.effective_from DESC,
                                    c.id DESC
                       ) AS rn
                FROM reg_prov_expr_capability c
                WHERE c.deleted = 0
                  AND c.lifecycle_status_code = 'ACTIVE'
                  AND c.provenance_id = #{provenanceId}
                  AND c.task_type_key IN (#{taskTypeKey}, 'ALL')
                  AND c.effective_from <= #{now}
                  AND (c.effective_to IS NULL OR c.effective_to > #{now})
            ) t
            WHERE t.rn = 1
            ORDER BY t.field_key
            """)
    List<RegProvExprCapabilityDO> selectActiveByTask(@Param("provenanceId") Long provenanceId,
                                                     @Param("taskTypeKey") String taskTypeKey,
                                                     @Param("now") Instant now);
}
