package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_rate_limit_cfg} 表的只读 Mapper。
 */
@Mapper
public interface RegProvRateLimitCfgMapper extends BaseMapper<RegProvRateLimitCfgDO> {

    @Select({"<script>",
            "SELECT *",
            "FROM reg_prov_rate_limit_cfg",
            "WHERE deleted = 0",
            "  AND lifecycle_status_code = 'ACTIVE'",
            "  AND provenance_id = #{provenanceId}",
            "  AND scope_code = #{scopeCode}",
            "  AND task_type_key = #{taskTypeKey}",
            "  AND effective_from &lt;= #{now}",
            "  AND (effective_to IS NULL OR effective_to &gt; #{now})",
            "  <if test='endpointId != null'>AND endpoint_id = #{endpointId}</if>",
            "  <if test='endpointId == null'>AND endpoint_id IS NULL</if>",
            "  <if test='credentialName != null'>AND credential_name = #{credentialName}</if>",
            "  <if test='credentialName == null'>AND credential_name IS NULL</if>",
            "ORDER BY effective_from DESC, id DESC",
            "LIMIT 1",
            "</script>"})
    Optional<RegProvRateLimitCfgDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                 @Param("scopeCode") String scopeCode,
                                                 @Param("taskTypeKey") String taskTypeKey,
                                                 @Param("endpointId") Long endpointId,
                                                 @Param("credentialName") String credentialName,
                                                 @Param("now") Instant now);
}
