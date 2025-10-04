package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Read-only mapper for {@code reg_prov_batching_cfg}.
 * Backed by SQL statements in {@code resources/mapper/RegProvBatchingCfgMapper.xml}.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvBatchingCfgMapper extends BaseMapper<RegProvBatchingCfgDO> {

    /**
     * Returns the most specific batching configuration for the given provenance and operation scope.
     */
    Optional<RegProvBatchingCfgDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                      @Param("operationTypeKey") String operationTypeKey,
                                                      @Param("now") Instant now);
}
