package com.patra.registry.infra.persistence.converter;

import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.Credential;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvCredentialDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * Provenance 相关实体到领域对象的转换器。
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ProvenanceEntityConverter {

    @Mapping(target = "code", source = "provenanceCode")
    @Mapping(target = "name", source = "provenanceName")
    @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
    Provenance toDomain(RegProvenanceDO entity);

    WindowOffsetConfig toDomain(RegProvWindowOffsetCfgDO entity);

    PaginationConfig toDomain(RegProvPaginationCfgDO entity);

    @Mapping(target = "tlsVerifyEnabled", expression = "java(Boolean.TRUE.equals(entity.getTlsVerifyEnabled()))")
    HttpConfig toDomain(RegProvHttpCfgDO entity);

    BatchingConfig toDomain(RegProvBatchingCfgDO entity);

    @Mapping(target = "retryOnNetworkError", expression = "java(Boolean.TRUE.equals(entity.getRetryOnNetworkError()))")
    RetryConfig toDomain(RegProvRetryCfgDO entity);

    RateLimitConfig toDomain(RegProvRateLimitCfgDO entity);

    @Mapping(target = "defaultPreferred", expression = "java(Boolean.TRUE.equals(entity.getIsDefaultPreferred()))")
    Credential toDomain(RegProvCredentialDO entity);

    List<Credential> toCredentialList(List<RegProvCredentialDO> entities);
}
