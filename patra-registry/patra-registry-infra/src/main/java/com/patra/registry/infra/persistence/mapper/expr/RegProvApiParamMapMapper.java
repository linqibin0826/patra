package com.patra.registry.infra.persistence.mapper.expr;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.expr.RegProvApiParamMapDO;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Read-only mapper for {@code reg_prov_api_param_map}.
 * Resolves provider-specific parameter mappings for standardized keys.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvApiParamMapMapper extends BaseMapper<RegProvApiParamMapDO> {

    /**
     * Fetches the most specific active mapping for the given provenance, operation and standard key.
     *
     * @param provenanceId      provenance identifier
     * @param operationTypeKey  normalized operation type key (ALL fallback supported)
     * @param operationCode     internal operation code
     * @param stdKey            standardized parameter key
     * @param now               evaluation timestamp
     * @return optional mapping effective at {@code now}
     */
    Optional<RegProvApiParamMapDO> selectActive(@Param("provenanceId") Long provenanceId,
                                                @Param("operationTypeKey") String operationTypeKey,
                                                @Param("operationCode") String operationCode,
                                                @Param("stdKey") String stdKey,
                                                @Param("now") Instant now);

    /**
     * Lists all active mappings for the specified scope, returning at most one row per
     * {@code (operation_code, std_key)} signature.
     *
     * @param provenanceId      provenance identifier
     * @param operationTypeKey  normalized operation type key
     * @param operationCode     internal operation code
     * @param now               evaluation timestamp
     * @return list of active mappings
     */
    List<RegProvApiParamMapDO> selectActiveByTask(@Param("provenanceId") Long provenanceId,
                                                  @Param("operationTypeKey") String operationTypeKey,
                                                  @Param("operationCode") String operationCode,
                                                  @Param("now") Instant now);
}
