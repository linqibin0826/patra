package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvCredentialDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.Instant;
import java.util.List;

/**
 * {@code reg_prov_credential} 表的只读 Mapper。
 */
@Mapper
public interface RegProvCredentialMapper extends BaseMapper<RegProvCredentialDO> {

    @Select({"<script>",
            "SELECT *",
            "FROM reg_prov_credential",
            "WHERE deleted = 0",
            "  AND lifecycle_status_code = 'ACTIVE'",
            "  AND provenance_id = #{provenanceId}",
            "  AND scope_code = #{scopeCode}",
            "  AND task_type_key = #{taskTypeKey}",
            "  <if test='endpointId != null'>AND endpoint_id = #{endpointId}</if>",
            "  <if test='endpointId == null'>AND endpoint_id IS NULL</if>",
            "  AND effective_from &lt;= #{now}",
            "  AND (effective_to IS NULL OR effective_to &gt; #{now})",
            "ORDER BY is_default_preferred DESC, effective_from DESC, id DESC",
            "</script>"})
    List<RegProvCredentialDO> selectActive(@Param("provenanceId") Long provenanceId,
                                           @Param("scopeCode") String scopeCode,
                                           @Param("taskTypeKey") String taskTypeKey,
                                           @Param("endpointId") Long endpointId,
                                           @Param("now") Instant now);
}
