package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvCredentialDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * {@code reg_prov_credential} 表的只读 Mapper。
 */

public interface RegProvCredentialMapper extends BaseMapper<RegProvCredentialDO> {

    /**
     * 合并查询（TASK/SOURCE + 精确/ALL + endpoint 精确/泛化），并按 默认标记 → effective_from DESC → id DESC 排序返回全部候选。
     */
    List<RegProvCredentialDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                 @Param("taskTypeKey") String taskTypeKey,
                                                 @Param("endpointId") Long endpointId,
                                                 @Param("now") Instant now);
}
