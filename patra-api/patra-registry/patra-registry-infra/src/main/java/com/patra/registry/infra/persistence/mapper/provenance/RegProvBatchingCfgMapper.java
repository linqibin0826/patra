package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_batching_cfg} 表的只读 Mapper。
 */
@Mapper
public interface RegProvBatchingCfgMapper extends BaseMapper<RegProvBatchingCfgDO> {

    /**
     * 合并查询（含 TASK/SOURCE + 精确/ALL + endpoint/credential 精确或泛化）并按确定性优先级挑选唯一记录。
     */
    Optional<RegProvBatchingCfgDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                      @Param("taskTypeKey") String taskTypeKey,
                                                      @Param("endpointId") Long endpointId,
                                                      @Param("credentialName") String credentialName,
                                                      @Param("now") Instant now);
}
