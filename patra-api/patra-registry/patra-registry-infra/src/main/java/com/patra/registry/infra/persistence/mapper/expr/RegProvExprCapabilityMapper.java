package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvExprCapabilityDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_expr_capability} 表的只读 Mapper。
 */
@Mapper
public interface RegProvExprCapabilityMapper extends BaseMapper<RegProvExprCapabilityDO> {

    /** 查询指定维度当前生效的字段能力。 */
    @Select("""
            SELECT *
            FROM reg_prov_expr_capability
            WHERE deleted = 0
              AND lifecycle_status_code = 'ACTIVE'
              AND provenance_id = #{provenanceId}
              AND scope_code = #{scopeCode}
              AND task_type_key = #{taskTypeKey}
              AND field_key = #{fieldKey}
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            ORDER BY effective_from DESC, id DESC
            LIMIT 1
            """)
    Optional<RegProvExprCapabilityDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                   @Param("scopeCode") String scopeCode,
                                                   @Param("taskTypeKey") String taskTypeKey,
                                                   @Param("fieldKey") String fieldKey,
                                                   @Param("now") Instant now);
}
