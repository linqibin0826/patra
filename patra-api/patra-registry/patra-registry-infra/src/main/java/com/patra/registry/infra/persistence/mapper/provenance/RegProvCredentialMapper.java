package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvCredentialDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/**
 * Read-only mapper for {@code reg_prov_credential}.
 * SQL implementation located in {@code resources/mapper/RegProvCredentialMapper.xml}.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvCredentialMapper extends BaseMapper<RegProvCredentialDO> {

    /**
     * Fetches active credential definitions ordered by preference, falling back to source-level scope.
     */
    List<RegProvCredentialDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                 @Param("operationTypeKey") String operationTypeKey,
                                                 @Param("now") Instant now);
}
