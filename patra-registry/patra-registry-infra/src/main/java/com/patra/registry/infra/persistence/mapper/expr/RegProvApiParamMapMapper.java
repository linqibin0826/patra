package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * {@code reg_prov_api_param_map} 表的只读 Mapper。
 */

public interface RegProvApiParamMapMapper extends BaseMapper<RegProvApiParamMapDO> {

    /**
     * 查询指定维度当前生效的参数映射。
     */
    @Select("""
            SELECT *
            FROM reg_prov_api_param_map
            WHERE deleted = 0
              AND lifecycle_status_code = 'ACTIVE'
              AND provenance_id = #{provenanceId}
              AND operation_type_key IN (#{operationTypeKey}, 'ALL')
              AND operation_code = #{operationCode}
              AND std_key = #{stdKey}
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            ORDER BY
              CASE WHEN operation_type_key = #{operationTypeKey} THEN 1 ELSE 2 END,
              effective_from DESC,
              id DESC
            LIMIT 1
            """)
    Optional<RegProvApiParamMapDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                @Param("operationTypeKey") String operationTypeKey,
                                                @Param("operationCode") String operationCode,
                                                @Param("stdKey") String stdKey,
                                                @Param("now") Instant now);

    /**
     * 查询指定任务维度（含 ALL 回退）下全部当前生效的参数映射。
     */
    @Select("""
            SELECT *
            FROM (
                SELECT m.*,
                       ROW_NUMBER() OVER (
                           PARTITION BY m.operation_code, m.std_key
                           ORDER BY CASE WHEN m.operation_type_key = #{operationTypeKey} THEN 1 ELSE 2 END,
                                    m.effective_from DESC,
                                    m.id DESC
                       ) AS rn
                FROM reg_prov_api_param_map m
                WHERE m.deleted = 0
                  AND m.lifecycle_status_code = 'ACTIVE'
                  AND m.provenance_id = #{provenanceId}
                  AND m.operation_type_key IN (#{operationTypeKey}, 'ALL')
                  AND m.operation_code = #{operationCode}
                  AND m.effective_from <= #{now}
                  AND (m.effective_to IS NULL OR m.effective_to > #{now})
            ) t
            WHERE t.rn = 1
            ORDER BY t.std_key
            """)
    List<RegProvApiParamMapDO> selectActiveByTask(@Param("provenanceId") Long provenanceId,
                                                  @Param("operationTypeKey") String operationTypeKey,
                                                  @Param("operationCode") String operationCode,
                                                  @Param("now") Instant now);
}
