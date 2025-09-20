package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_http_cfg} 表的只读 Mapper。
 */
@Mapper
public interface RegProvHttpCfgMapper extends BaseMapper<RegProvHttpCfgDO> {

    @Select("""
            SELECT *
            FROM reg_prov_http_cfg
            WHERE deleted = 0
              AND lifecycle_status_code = 'ACTIVE'
              AND provenance_id = #{provenanceId}
              AND scope_code = #{scopeCode}
              AND task_type_key = #{taskTypeKey}
              AND effective_from <= #{now}
              AND (effective_to IS NULL OR effective_to > #{now})
            ORDER BY effective_from DESC, id DESC
            LIMIT 1
            """)
    Optional<RegProvHttpCfgDO> selectActive(@Param("provenanceId") Long provenanceId,
                                            @Param("scopeCode") String scopeCode,
                                            @Param("taskTypeKey") String taskTypeKey,
                                            @Param("now") Instant now);
}
