package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.apache.ibatis.annotations.Param;

/// 只读 Mapper,用于表 `reg_prov_api_param_map`. Resolves provider-specific parameter mappings for
/// standardized keys.
/// 
/// @author linqibin
/// @since 0.1.0
public interface RegProvApiParamMapMapper extends BaseMapper<RegProvApiParamMapDO> {

  /// Fetches the most specific active mapping for the given provenance, endpoint and standard key.
/// 
/// @param provenanceId provenance identifier
/// @param operationType normalized operation type (ALL fallback supported)
/// @param endpointName endpoint name (NULL means all endpoints)
/// @param stdKey standardized parameter key
/// @param now evaluation timestamp
/// @return optional mapping effective at `now`
  Optional<RegProvApiParamMapDO> selectActive(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("endpointName") String endpointName,
      @Param("stdKey") String stdKey,
      @Param("now") Instant now);

  /// Lists all active mappings for the specified scope, returning at most one row per `(endpoint_name, std_key)` signature.
/// 
/// @param provenanceId provenance identifier
/// @param operationType normalized operation type
/// @param endpointName endpoint name (NULL means all endpoints)
/// @param now evaluation timestamp
/// @return list of active mappings
  List<RegProvApiParamMapDO> selectActiveByTask(
      @Param("provenanceId") Long provenanceId,
      @Param("operationType") String operationType,
      @Param("endpointName") String endpointName,
      @Param("now") Instant now);
}
