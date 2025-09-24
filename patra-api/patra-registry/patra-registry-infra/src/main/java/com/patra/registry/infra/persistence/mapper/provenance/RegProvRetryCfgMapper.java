package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_retry_cfg} 表的只读 Mapper。
 */
@Mapper
public interface RegProvRetryCfgMapper extends BaseMapper<RegProvRetryCfgDO> {

    Optional<RegProvRetryCfgDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                   @Param("taskTypeKey") String taskTypeKey,
                                                   @Param("now") Instant now);
}
