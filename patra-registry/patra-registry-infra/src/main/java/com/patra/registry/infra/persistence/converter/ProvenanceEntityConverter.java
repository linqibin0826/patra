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

/// 数据源实体转换器,负责将数据库实体转换为领域值对象。
///
/// 转换规则:
///
/// - 使用 MapStruct 自动映射字段
///   - 处理布尔字段的 `TINYINT(1)` 到 `Boolean` 转换
///   - 通过辅助方法处理 JSON 字段序列化
///
/// 注意:仅在查询侧使用,用于组装数据源配置快照。
///
/// @author linqibin
/// @since 0.1.0
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

  /// MapStruct 辅助方法:将 JsonNode 序列化为紧凑 JSON 字符串。
  ///
  /// 用于领域 VO 将 JSON 保持为 String 类型的场景。
  ///
  /// @param node JSON 节点
  /// @return JSON 字符串或 null
  default String map(JsonNode node) {
    return node == null ? null : node.toString();
  }
}
