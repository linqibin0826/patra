package com.patra.registry.infra.adapter.persistence.converter.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.registry.domain.model.vo.expr.ApiParamMapping;
import com.patra.registry.domain.model.vo.expr.ExprCapability;
import com.patra.registry.domain.model.vo.expr.ExprField;
import com.patra.registry.domain.model.vo.expr.ExprRenderRule;
import com.patra.registry.infra.adapter.persistence.entity.expr.ExprFieldDictEntity;
import com.patra.registry.infra.adapter.persistence.entity.expr.ProvApiParamMapEntity;
import com.patra.registry.infra.adapter.persistence.entity.expr.ProvExprCapabilityEntity;
import com.patra.registry.infra.adapter.persistence.entity.expr.ProvExprRenderRuleEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

/// 表达式 JPA 实体转换器，负责将 JPA 实体转换为领域值对象。
///
/// 转换规则：
///
/// - 使用 MapStruct 自动映射字段
/// - 处理布尔字段的转换
/// - 通过辅助方法处理 JSON 字段序列化
/// - 映射表达式能力的复杂字段(操作符、标记匹配等)
///
/// 注意：仅在 CQRS 读侧使用。
///
/// @author linqibin
/// @since 0.1.0
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ExprJpaMapper {

  /// 转换表达式字段字典实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 表达式字段领域值对象
  @Mapping(target = "exposable", expression = "java(Boolean.TRUE.equals(entity.getExposable()))")
  @Mapping(target = "dateField", expression = "java(Boolean.TRUE.equals(entity.getDateField()))")
  ExprField toDomain(ExprFieldDictEntity entity);

  /// 转换 API 参数映射实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return API 参数映射领域值对象
  @Mapping(target = "notesJson", source = "notes")
  ApiParamMapping toDomain(ProvApiParamMapEntity entity);

  /// 转换表达式能力实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 表达式能力领域值对象
  @Mapping(target = "opsJson", source = "ops")
  @Mapping(target = "negatableOpsJson", source = "negatableOps")
  @Mapping(
      target = "supportsNot",
      expression = "java(Boolean.TRUE.equals(entity.getSupportsNot()))")
  @Mapping(target = "termMatchesJson", source = "termMatches")
  @Mapping(
      target = "termCaseSensitiveAllowed",
      expression = "java(Boolean.TRUE.equals(entity.getTermCaseSensitiveAllowed()))")
  @Mapping(
      target = "termAllowBlank",
      expression = "java(Boolean.TRUE.equals(entity.getTermAllowBlank()))")
  @Mapping(
      target = "inCaseSensitiveAllowed",
      expression = "java(Boolean.TRUE.equals(entity.getInCaseSensitiveAllowed()))")
  @Mapping(
      target = "rangeAllowOpenStart",
      expression = "java(Boolean.TRUE.equals(entity.getRangeAllowOpenStart()))")
  @Mapping(
      target = "rangeAllowOpenEnd",
      expression = "java(Boolean.TRUE.equals(entity.getRangeAllowOpenEnd()))")
  @Mapping(
      target = "rangeAllowClosedAtInfinity",
      expression = "java(Boolean.TRUE.equals(entity.getRangeAllowClosedAtInfty()))")
  @Mapping(
      target = "existsSupported",
      expression = "java(Boolean.TRUE.equals(entity.getExistsSupported()))")
  @Mapping(target = "tokenKindsJson", source = "tokenKinds")
  ExprCapability toDomain(ProvExprCapabilityEntity entity);

  /// 转换表达式渲染规则实体为领域值对象。
  ///
  /// @param entity JPA 实体
  /// @return 表达式渲染规则领域值对象
  @Mapping(target = "negated", expression = "java(entity.getNegated())")
  @Mapping(target = "wrapGroup", expression = "java(Boolean.TRUE.equals(entity.getWrapGroup()))")
  @Mapping(target = "paramsJson", source = "params")
  @Mapping(target = "functionCode", source = "fnCode")
  ExprRenderRule toDomain(ProvExprRenderRuleEntity entity);

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
