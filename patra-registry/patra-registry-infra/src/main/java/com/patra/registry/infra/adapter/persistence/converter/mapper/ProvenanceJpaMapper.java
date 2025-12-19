package com.patra.registry.infra.adapter.persistence.converter.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.domain.model.vo.provenance.BatchingConfig;
import com.patra.registry.domain.model.vo.provenance.HttpConfig;
import com.patra.registry.domain.model.vo.provenance.PaginationConfig;
import com.patra.registry.domain.model.vo.provenance.Provenance;
import com.patra.registry.domain.model.vo.provenance.RateLimitConfig;
import com.patra.registry.domain.model.vo.provenance.RetryConfig;
import com.patra.registry.domain.model.vo.provenance.WindowOffsetConfig;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvBatchingCfgEntity;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvHttpCfgEntity;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvPaginationCfgEntity;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvRateLimitCfgEntity;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvRetryCfgEntity;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvWindowOffsetCfgEntity;
import com.patra.registry.infra.adapter.persistence.entity.provenance.ProvenanceEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 数据源 JPA 实体转换器，负责将 JPA 实体转换为领域值对象。
///
/// 转换规则：
///
/// - 使用 MapStruct 自动映射字段
/// - 处理布尔字段的转换
/// - 通过辅助方法处理 JSON 字段序列化
///
/// 注意：仅在查询侧使用，用于组装数据源配置快照。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProvenanceJpaMapper {

  /// 转换数据源实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 数据源领域值对象
  @Mapping(target = "code", source = "provenanceCode")
  @Mapping(target = "name", source = "provenanceName")
  @Mapping(target = "active", expression = "java(Boolean.TRUE.equals(entity.getIsActive()))")
  Provenance toDomain(ProvenanceEntity entity);

  /// 转换窗口偏移配置实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 窗口偏移配置领域值对象
  WindowOffsetConfig toDomain(ProvWindowOffsetCfgEntity entity);

  /// 转换分页配置实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 分页配置领域值对象
  PaginationConfig toDomain(ProvPaginationCfgEntity entity);

  /// 转换 HTTP 配置实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return HTTP 配置领域值对象
  @Mapping(
      target = "tlsVerifyEnabled",
      expression = "java(Boolean.TRUE.equals(entity.getTlsVerifyEnabled()))")
  HttpConfig toDomain(ProvHttpCfgEntity entity);

  /// 转换批处理配置实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 批处理配置领域值对象
  BatchingConfig toDomain(ProvBatchingCfgEntity entity);

  /// 转换重试配置实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 重试配置领域值对象
  @Mapping(
      target = "retryOnNetworkError",
      expression = "java(Boolean.TRUE.equals(entity.getRetryOnNetworkError()))")
  RetryConfig toDomain(ProvRetryCfgEntity entity);

  /// 转换速率限制配置实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 速率限制配置领域值对象
  RateLimitConfig toDomain(ProvRateLimitCfgEntity entity);

  /// MapStruct 辅助方法：将 JsonNode 序列化为紧凑 JSON 字符串。
  ///
  /// 用于领域 VO 将 JSON 保持为 String 类型的场景。
  ///
  /// @param node JSON 节点
  /// @return JSON 字符串或 null
  default String map(JsonNode node) {
    return node == null ? null : node.toString();
  }
}
