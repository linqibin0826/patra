package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * Read-only mapper for {@code reg_prov_pagination_cfg}.
 * Corresponding SQL lives in {@code resources/mapper/RegProvPaginationCfgMapper.xml}.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvPaginationCfgMapper extends BaseMapper<RegProvPaginationCfgDO> {

    /**
     * Returns the effective pagination configuration for the given scope, falling back to {@code ALL}.
     */
    Optional<RegProvPaginationCfgDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                        @Param("operationType") String operationType,
                                                        @Param("now") Instant now);
}
