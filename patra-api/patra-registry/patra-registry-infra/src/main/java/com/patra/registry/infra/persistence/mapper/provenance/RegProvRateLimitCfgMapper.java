package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/**
 * Read-only mapper for {@code reg_prov_rate_limit_cfg}. SQL implementation located in {@code
 * resources/mapper/RegProvRateLimitCfgMapper.xml}.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvRateLimitCfgMapper extends BaseMapper<RegProvRateLimitCfgDO> {

  /** Retrieves the effective rate limit configuration scoped by provenance and operation. */
  Optional<RegProvRateLimitCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
