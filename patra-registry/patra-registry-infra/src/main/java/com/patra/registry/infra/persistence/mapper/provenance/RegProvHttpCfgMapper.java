package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO;
import java.time.Instant;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_http_cfg`. Backed by XML queries located at `resources/mapper/RegProvHttpCfgMapper.xml`.
/// 
/// @author linqibin
/// @since 0.1.0
public interface RegProvHttpCfgMapper extends BaseMapper<RegProvHttpCfgDO> {

  /// Retrieves the effective HTTP configuration for the given provenance and operation scope.
  Optional<RegProvHttpCfgDO> selectActiveMerged(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("now") Instant now);
}
