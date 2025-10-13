package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/**
 * Read-only mapper for {@code reg_prov_retry_cfg}. SQL statements are defined in {@code
 * resources/mapper/RegProvRetryCfgMapper.xml}.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvRetryCfgMapper extends BaseMapper<RegProvRetryCfgDO> {

  /** Fetches the retry configuration effective for the specified provenance/operation scope. */
  Optional<RegProvRetryCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
