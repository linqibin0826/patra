package com.patra.registry.infra.persistence.mapper.provenance;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.patra.registry.infra.persistence.entity.provenance.RegProvEndpointDefDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.Optional;

/**
 * {@code reg_prov_endpoint_def} 表的只读 Mapper。
 */
@Mapper
public interface RegProvEndpointDefMapper extends BaseMapper<RegProvEndpointDefDO> {

    /**
     * 合并查询（TASK/SOURCE + 精确/ALL）并基于确定性优先级返回唯一端点定义。
     */
    Optional<RegProvEndpointDefDO> selectActiveMerged(@Param("provenanceId") Long provenanceId,
                                                      @Param("taskTypeKey") String taskTypeKey,
                                                      @Param("endpoint") String endpoint,
                                                      @Param("now") Instant now);
}
