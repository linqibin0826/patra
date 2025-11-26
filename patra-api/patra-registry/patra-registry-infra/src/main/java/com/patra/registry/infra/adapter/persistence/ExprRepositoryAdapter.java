package com.patra.registry.infra.adapter.persistence;

import com.patra.common.enums.ProvenanceCode;
import com.patra.registry.domain.exception.provenance.ProvenanceNotFoundException;
import com.patra.registry.domain.model.vo.expr.*;
import com.patra.registry.domain.port.ExprRepository;
import com.patra.registry.domain.support.RegistryKeyStandardizer;
import com.patra.registry.infra.persistence.converter.ExprEntityConverter;
import com.patra.registry.infra.persistence.mapper.expr.RegExprFieldDictMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvApiParamMapMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprCapabilityMapper;
import com.patra.registry.infra.persistence.mapper.expr.RegProvExprRenderRuleMapper;
import com.patra.registry.infra.persistence.mapper.provenance.RegProvenanceMapper;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

/// 表达式元数据仓储实现,基于 MyBatis-Plus。
///
/// 实现策略:
///
/// - 聚合字段字典、能力、渲染规则和 API 参数映射以构建领域快照
///   - 优先使用操作特定配置,当专用切片不可用时回退到 `ALL`
///
/// 日志策略:
///
/// - DEBUG 级别记录所有查询操作和转换过程
///   - WARN 级别记录数据源代码未找到等异常情况
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@Repository
@RequiredArgsConstructor
public class ExprRepositoryAdapter implements ExprRepository {

  private final RegExprFieldDictMapper fieldDictMapper;
  private final RegProvApiParamMapMapper apiParamMapMapper;
  private final RegProvExprCapabilityMapper capabilityMapper;
  private final RegProvExprRenderRuleMapper renderRuleMapper;
  private final RegProvenanceMapper provenanceMapper;
  private final ExprEntityConverter converter;

  /// 加载指定数据源和操作的表达式元数据快照。
  ///
  /// 聚合字段字典、能力、渲染规则和 API 参数映射。
  ///
  /// @param provenanceCode 数据源代码
  /// @param operationType 操作类型键
  /// @param endpointName API 端点名称(对于端点无关查询可为 null)
  /// @param at 查询时间戳(null 表示当前时间)
  /// @return 完整的表达式元数据快照
  @Override
  public ExprSnapshot loadSnapshot(
      ProvenanceCode provenanceCode, String operationType, String endpointName, Instant at) {
    Instant timestamp = atOrNow(at);
    Long provenanceId = resolveProvenanceId(provenanceCode);

    String operationKey = RegistryKeyStandardizer.toOperationKeyOrAll(operationType);
    String normalizedEndpoint =
        (endpointName == null || endpointName.isBlank())
            ? null
            : RegistryKeyStandardizer.toUppercaseCode(endpointName);

    List<ExprField> fields = loadFields();
    List<ExprCapability> capabilities = loadCapabilities(provenanceId, operationKey, timestamp);
    List<ExprRenderRule> renderRules = loadRenderRules(provenanceId, operationKey, timestamp);
    List<ApiParamMapping> apiParams =
        loadApiParamMappings(provenanceId, operationKey, normalizedEndpoint, timestamp);

    return new ExprSnapshot(fields, capabilities, renderRules, apiParams);
  }

  /// 从字段字典加载所有激活的表达式字段。
  ///
  /// @return 激活的表达式字段列表
  private List<ExprField> loadFields() {
    log.debug("Querying all active expression fields from field dictionary");
    List<ExprField> fields =
        fieldDictMapper.selectAllActive().stream().map(converter::toDomain).toList();
    log.debug(
        "Converting {} ExprFieldDictDO entities to domain models, field keys: {}",
        fields.size(),
        fields.stream().map(ExprField::fieldKey).limit(10).toList()
            + (fields.size() > 10 ? "..." : ""));
    return fields;
  }

  /// 加载指定数据源和操作的表达式能力。
  ///
  /// @param provenanceId 数据源 ID
  /// @param operationKey 规范化的操作键
  /// @param timestamp 查询时间戳
  /// @return 表达式能力列表
  private List<ExprCapability> loadCapabilities(
      Long provenanceId, String operationKey, Instant timestamp) {
    log.debug(
        "Querying capabilities from database: provenanceId [{}], operationKey [{}], timestamp [{}]",
        provenanceId,
        operationKey,
        timestamp);
    List<ExprCapability> capabilities =
        capabilityMapper.selectActiveByTask(provenanceId, operationKey, timestamp).stream()
            .map(converter::toDomain)
            .toList();
    log.debug(
        "Converting {} ExprCapabilityDO entities to domain models, field keys: {}",
        capabilities.size(),
        capabilities.stream().map(ExprCapability::fieldKey).toList());
    return capabilities;
  }

  /// 加载指定数据源和操作的表达式渲染规则。
  ///
  /// @param provenanceId 数据源 ID
  /// @param operationKey 规范化的操作键
  /// @param timestamp 查询时间戳
  /// @return 表达式渲染规则列表
  private List<ExprRenderRule> loadRenderRules(
      Long provenanceId, String operationKey, Instant timestamp) {
    log.debug(
        "Querying render rules from database: provenanceId [{}], operationKey [{}], timestamp [{}]",
        provenanceId,
        operationKey,
        timestamp);
    List<ExprRenderRule> renderRules =
        renderRuleMapper.selectActiveByTask(provenanceId, operationKey, timestamp).stream()
            .map(converter::toDomain)
            .toList();
    log.debug(
        "Converting {} ExprRenderRuleDO entities to domain models for {} unique fields",
        renderRules.size(),
        renderRules.stream().map(ExprRenderRule::fieldKey).distinct().count());
    return renderRules;
  }

  /// 加载指定数据源、操作和端点的 API 参数映射。
  ///
  /// @param provenanceId 数据源 ID
  /// @param operationKey 规范化的操作键
  /// @param normalizedEndpoint 规范化的端点名称(对于端点无关查询可为 null)
  /// @param timestamp 查询时间戳
  /// @return API 参数映射列表
  private List<ApiParamMapping> loadApiParamMappings(
      Long provenanceId, String operationKey, String normalizedEndpoint, Instant timestamp) {
    log.debug(
        "Querying API parameter mappings from database: provenanceId [{}], operationKey [{}], endpoint [{}], timestamp [{}]",
        provenanceId,
        operationKey,
        normalizedEndpoint,
        timestamp);
    List<ApiParamMapping> apiParams =
        apiParamMapMapper
            .selectActiveByTask(provenanceId, operationKey, normalizedEndpoint, timestamp)
            .stream()
            .map(converter::toDomain)
            .toList();
    log.debug(
        "Converting {} ApiParamMapDO entities to domain models, standard keys: {}",
        apiParams.size(),
        apiParams.stream().map(ApiParamMapping::stdKey).toList());
    return apiParams;
  }

  /// 返回提供的时间戳,如果为 null 则返回当前时间。
  ///
  /// @param at 要检查的时间戳
  /// @return 时间戳或当前时间
  private Instant atOrNow(Instant at) {
    return at != null ? at : Instant.now();
  }

  /// 从业务代码解析数据源 ID。
  ///
  /// @param provenanceCode 数据源代码
  /// @return 数据源 ID
  /// @throws ProvenanceNotFoundException 如果数据源代码未找到
  private Long resolveProvenanceId(ProvenanceCode provenanceCode) {
    String code = provenanceCode.getCode();
    log.debug("Querying provenance ID for code [{}] from database", code);
    return provenanceMapper
        .selectByCode(code)
        .map(
            entity -> {
              log.debug("Resolved provenance code [{}] to ID [{}]", code, entity.getId());
              return entity.getId();
            })
        .orElseThrow(
            () -> {
              log.warn("Provenance code not found: {}", code);
              return new ProvenanceNotFoundException("Provenance code not found: " + code);
            });
  }
}
