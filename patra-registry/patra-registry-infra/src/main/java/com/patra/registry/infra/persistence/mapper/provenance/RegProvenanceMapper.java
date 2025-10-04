package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/**
 * Read-only mapper for {@code reg_provenance}.
 * Provides helper queries to load provenance metadata by business code.
 *
 * @author linqibin
 * @since 0.1.0
 */
public interface RegProvenanceMapper extends BaseMapper<RegProvenanceDO> {

    /**
     * Fetches an active provenance row by its stable business code.
     *
     * @param code provenance code (e.g., pubmed)
     * @return optional provenance definition
     */
    Optional<RegProvenanceDO> selectByCode(@Param("code") String code);

    /**
     * Lists all active provenance definitions ordered by code.
     *
     * @return provenance list
     */
    List<RegProvenanceDO> selectAllActive();
}
