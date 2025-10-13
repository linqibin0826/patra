package com.patra.registry.infra.persistence.converter;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import com.patra.registry.infra.persistence.entity.provenance.RegProvBatchingCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvHttpCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvPaginationCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRateLimitCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvRetryCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvWindowOffsetCfgDO;
import com.patra.registry.infra.persistence.entity.provenance.RegProvenanceDO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct converter turning provenance-related persistence entities into domain view models. Used
 * exclusively on the query side to assemble provenance configuration snapshots.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProvenanceEntityConverter {

  @Mapping(target = "code", source = "provenanceCode")
  @Mapping(target = "name", source = "provenanceName")
  @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
  Provenance toDomain(RegProvenanceDO entity);

  WindowOffsetConfig toDomain(RegProvWindowOffsetCfgDO entity);

  PaginationConfig toDomain(RegProvPaginationCfgDO entity);

  @Mapping(
      target = "tlsVerifyEnabled",
      expression = "java(Boolean.TRUE.equals(entity.getTlsVerifyEnabled()))")
  HttpConfig toDomain(RegProvHttpCfgDO entity);

  BatchingConfig toDomain(RegProvBatchingCfgDO entity);

  @Mapping(
      target = "retryOnNetworkError",
      expression = "java(Boolean.TRUE.equals(entity.getRetryOnNetworkError()))")
  RetryConfig toDomain(RegProvRetryCfgDO entity);

  RateLimitConfig toDomain(RegProvRateLimitCfgDO entity);

  /**
   * MapStruct helper: serialize JsonNode to compact JSON string for domain VOs that keep JSON as
   * String.
   */
  default String map(JsonNode node) {
    return node == null ? null : node.toString();
  }
}
