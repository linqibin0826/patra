package com.patra.starter.expr.compiler.snapshot.convert;

import com.patra.expr.Atom;
import com.patra.starter.expr.compiler.snapshot.ProvenanceSnapshot;
import dev.linqibin.patra.common.enums.RegistryConfigScope;
import dev.linqibin.patra.registry.api.dto.expr.ApiParamMappingResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprCapabilityResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprFieldResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprRenderRuleResp;
import dev.linqibin.patra.registry.api.dto.expr.ExprSnapshotResp;
import dev.linqibin.patra.registry.api.dto.provenance.ProvenanceResp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/// 将注册表 DTO 转换为启动器的不可变 {@link ProvenanceSnapshot} 模型。
///
/// @author linqibin
/// @since 0.1.0
@SuppressWarnings("unused")
public class SnapshotAssembler {

  private final ObjectMapper objectMapper;

  /// 构造快照组装器。
  ///
  /// @param objectMapper JSON 对象映射器（必需，用于解析 JSON 字段）
  public SnapshotAssembler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /// 组装 Provenance 快照。
  ///
  /// @param provenance 数据源响应
  /// @param snapshot 表达式快照响应
  /// @param operationType 操作类型
  /// @param endpointName 端点名称
  /// @return Provenance 快照
  public ProvenanceSnapshot assemble(
      ProvenanceResp provenance,
      ExprSnapshotResp snapshot,
      String operationType,
      String endpointName) {
    Objects.requireNonNull(provenance, "provenance");
    Map<String, ProvenanceSnapshot.FieldDefinition> fields = new HashMap<>();
    for (ExprFieldResp field : nullSafe(snapshot != null ? snapshot.fields() : null)) {
      fields.put(
          field.fieldKey(),
          new ProvenanceSnapshot.FieldDefinition(
              field.fieldKey(),
              field.displayName(),
              field.description(),
              ProvenanceSnapshot.DataType.valueOf(field.dataTypeCode().toUpperCase(Locale.ROOT)),
              ProvenanceSnapshot.Cardinality.valueOf(
                  field.cardinalityCode().toUpperCase(Locale.ROOT)),
              field.exposable(),
              field.dateField()));
    }

    Map<String, ProvenanceSnapshot.Capability> capabilities = new HashMap<>();
    for (ExprCapabilityResp capability :
        nullSafe(snapshot != null ? snapshot.capabilities() : null)) {
      capabilities.put(capability.fieldKey(), toCapability(capability));
    }

    Map<String, ProvenanceSnapshot.ApiParameter> apiParameters = new HashMap<>();
    for (ApiParamMappingResp mapping :
        nullSafe(snapshot != null ? snapshot.apiParamMappings() : null)) {
      apiParameters.put(
          mapping.stdKey(),
          new ProvenanceSnapshot.ApiParameter(
              mapping.stdKey(),
              mapping.providerParamName(),
              mapping.transformCode(),
              mapping.notesJson()));
    }

    List<ProvenanceSnapshot.RenderRule> renderRules = new ArrayList<>();
    for (ExprRenderRuleResp rule : nullSafe(snapshot != null ? snapshot.renderRules() : null)) {
      renderRules.add(toRenderRule(rule));
    }

    String normalizedOperationType = normalizeOperationType(operationType);
    String scopeCode =
        normalizedOperationType == null
            ? RegistryConfigScope.SOURCE.code()
            : RegistryConfigScope.TASK.code();

    return new ProvenanceSnapshot(
        new ProvenanceSnapshot.Identity(provenance.id(), provenance.code(), provenance.name()),
        new ProvenanceSnapshot.Scope(scopeCode, normalizedOperationType),
        new ProvenanceSnapshot.Operation(endpointName, provenance.timezoneDefault()),
        0L,
        Instant.now(),
        fields,
        capabilities,
        apiParameters,
        renderRules);
  }

  /// 转换字段能力。
  ///
  /// @param resp 字段能力响应
  /// @return 字段能力快照
  private ProvenanceSnapshot.Capability toCapability(ExprCapabilityResp resp) {
    Set<String> ops = toSet(resp.opsJson());
    Set<String> negOps = toSet(resp.negatableOpsJson());
    Set<String> termMatches = toSet(resp.termMatchesJson());
    Set<String> tokenKinds = toSet(resp.tokenKindsJson());
    return new ProvenanceSnapshot.Capability(
        ops,
        negOps,
        resp.supportsNot(),
        termMatches,
        resp.termCaseSensitiveAllowed(),
        resp.termAllowBlank(),
        resp.termMinLength(),
        resp.termMaxLength(),
        resp.termPattern(),
        resp.inMaxSize(),
        resp.inCaseSensitiveAllowed(),
        parseRangeKind(resp.rangeKindCode()),
        resp.rangeAllowOpenStart(),
        resp.rangeAllowOpenEnd(),
        resp.rangeAllowClosedAtInfinity(),
        resp.dateMin(),
        resp.dateMax(),
        resp.datetimeMin(),
        resp.datetimeMax(),
        resp.numberMin() == null ? null : resp.numberMin().toPlainString(),
        resp.numberMax() == null ? null : resp.numberMax().toPlainString(),
        resp.existsSupported(),
        tokenKinds,
        resp.tokenValuePattern());
  }

  /// 转换渲染规则。
  ///
  /// @param resp 渲染规则响应
  /// @return 渲染规则快照
  private ProvenanceSnapshot.RenderRule toRenderRule(ExprRenderRuleResp resp) {
    Map<String, String> params = parseParams(resp.paramsJson());
    String normalizedOperationType = normalizeOperationType(resp.operationType());
    String scopeCode =
        normalizedOperationType == null
            ? RegistryConfigScope.SOURCE.code()
            : RegistryConfigScope.TASK.code();

    return new ProvenanceSnapshot.RenderRule(
        resp.fieldKey(),
        scopeCode,
        normalizedOperationType,
        Atom.Operator.valueOf(resp.opCode().toUpperCase(Locale.ROOT)),
        resp.matchTypeCode(),
        toNegationQualifier(resp.negated()),
        toValueType(resp.valueTypeCode()),
        ProvenanceSnapshot.EmitType.valueOf(resp.emitTypeCode().toUpperCase(Locale.ROOT)),
        resp.template(),
        resp.itemTemplate(),
        resp.joiner(),
        resp.wrapGroup(),
        params,
        resp.functionCode(),
        resp.effectiveFrom(),
        resp.effectiveTo(),
        0);
  }

  /// 规范化操作类型。
  ///
  /// @param operationType 原始操作类型
  /// @return 规范化后的操作类型（大写，null 或空白返回 null）
  private String normalizeOperationType(String operationType) {
    if (operationType == null || operationType.isBlank()) {
      return null;
    }
    return operationType.trim().toUpperCase(Locale.ROOT);
  }

  /// 解析参数 JSON。
  ///
  /// @param paramsJson 参数 JSON 字符串
  /// @return 参数映射
  private Map<String, String> parseParams(String paramsJson) {
    if (paramsJson == null || paramsJson.isBlank()) {
      return Map.of();
    }
    try {
      @SuppressWarnings("unchecked")
      Map<String, String> map = objectMapper.readValue(paramsJson, Map.class);
      return Map.copyOf(map);
    } catch (JacksonException e) {
      throw new IllegalStateException("解析渲染规则参数失败", e);
    }
  }

  /// 将 JSON 字符串转换为 Set。
  ///
  /// @param json JSON 字符串
  /// @return 字符串集合
  private Set<String> toSet(String json) {
    if (json == null || json.isBlank()) {
      return Set.of();
    }
    try {
      @SuppressWarnings("unchecked")
      List<String> list = objectMapper.readValue(json, List.class);
      return new HashSet<>(list);
    } catch (JacksonException e) {
      throw new IllegalStateException("解析 JSON 数组失败: " + json, e);
    }
  }

  /// 解析范围类型。
  ///
  /// @param code 范围类型代码
  /// @return 范围类型枚举
  private ProvenanceSnapshot.RangeKind parseRangeKind(String code) {
    if (code == null || code.isBlank()) {
      return ProvenanceSnapshot.RangeKind.NONE;
    }
    return ProvenanceSnapshot.RangeKind.valueOf(code.toUpperCase(Locale.ROOT));
  }

  /// 转换取反限定符。
  ///
  /// @param value 布尔值（null 表示 ANY）
  /// @return 取反限定符枚举
  private ProvenanceSnapshot.NegationQualifier toNegationQualifier(Boolean value) {
    if (value == null) {
      return ProvenanceSnapshot.NegationQualifier.ANY;
    }
    return value
        ? ProvenanceSnapshot.NegationQualifier.TRUE
        : ProvenanceSnapshot.NegationQualifier.FALSE;
  }

  /// 转换值类型。
  ///
  /// @param code 值类型代码
  /// @return 值类型枚举
  private ProvenanceSnapshot.ValueType toValueType(String code) {
    if (code == null || code.isBlank()) {
      return ProvenanceSnapshot.ValueType.ANY;
    }
    return ProvenanceSnapshot.ValueType.valueOf(code.toUpperCase(Locale.ROOT));
  }

  /// 空安全列表处理。
  ///
  /// @param list 可能为 null 的列表
  /// @param <T> 列表元素类型
  /// @return 非 null 的列表（null 时返回空列表）
  private <T> List<T> nullSafe(List<T> list) {
    return list == null ? List.of() : list;
  }
}
