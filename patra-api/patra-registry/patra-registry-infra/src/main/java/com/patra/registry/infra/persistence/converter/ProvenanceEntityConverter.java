package com.patra.registry.infra.persistence.converter;

import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.Credential;
import com.patra.registry.domain.model.vo.provenance.EndpointDefinition;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvCredentialDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvEndpointDefDO;
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

    @Mapping(target = "defaultQueryParamsJson", source = "defaultQueryParams")
    @Mapping(target = "defaultBodyPayloadJson", source = "defaultBodyPayload")
    @Mapping(target = "authRequired", expression = "java(Boolean.TRUE.equals(entity.getIsAuthRequired()))")
    EndpointDefinition toDomain(RegProvEndpointDefDO entity);

    WindowOffsetConfig toDomain(RegProvWindowOffsetCfgDO entity);

    PaginationConfig toDomain(RegProvPaginationCfgDO entity);

    @Mapping(target = "tlsVerifyEnabled", expression = "java(Boolean.TRUE.equals(entity.getTlsVerifyEnabled()))")
    @Mapping(target = "acceptCompressEnabled", expression = "java(Boolean.TRUE.equals(entity.getAcceptCompressEnabled()))")
    @Mapping(target = "preferHttp2Enabled", expression = "java(Boolean.TRUE.equals(entity.getPreferHttp2Enabled()))")
    HttpConfig toDomain(RegProvHttpCfgDO entity);

    @Mapping(target = "preferCompactPayload", expression = "java(Boolean.TRUE.equals(entity.getPreferCompactPayload()))")
    @Mapping(target = "requestTemplateJson", source = "requestTemplateJson")
    BatchingConfig toDomain(RegProvBatchingCfgDO entity);

    @Mapping(target = "retryOnNetworkError", expression = "java(Boolean.TRUE.equals(entity.getRetryOnNetworkError()))")
    RetryConfig toDomain(RegProvRetryCfgDO entity);

    @Mapping(target = "respectServerRateHeader", expression = "java(Boolean.TRUE.equals(entity.getRespectServerRateHeader()))")
    RateLimitConfig toDomain(RegProvRateLimitCfgDO entity);

    @Mapping(target = "defaultPreferred", expression = "java(Boolean.TRUE.equals(entity.getIsDefaultPreferred()))")
    Credential toDomain(RegProvCredentialDO entity);

    List<Credential> toCredentialList(List<RegProvCredentialDO> entities);
}
