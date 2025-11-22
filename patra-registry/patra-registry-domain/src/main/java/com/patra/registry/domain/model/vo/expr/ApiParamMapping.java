package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.support.TemporalEntity;
import java.time.Instant;

/// API 参数映射领域值对象,对应表 `reg_prov_api_param_map`。
///
/// 在 SOURCE/TASK 范围内将统一的标准键映射到提供者特定的参数名称。仅负责键名映射; 值级别的转换仅通过 transform_code 声明。
///
/// @author linqibin
/// @since 0.1.0
public record ApiParamMapping(
    /* 主键;唯一映射标识符 */
    Long id,
    /* 外键,引用 reg_provenance.id */
    Long provenanceId,
    /* 操作类型区分器 (HARVEST/UPDATE/BACKFILL/SANDBOX);null 表示应用于所有类型 */
    String operationType,
    /* 此映射应用的端点名称;null 表示所有端点 */
    String endpointName,
    /* 标准键(统一的内部语义键)通常在渲染期间产生(例如,from/to/ti/ab) */
    String stdKey,
    /* 提供者参数名称:具体的 HTTP 参数(例如,mindate/maxdate/term/retmax) */
    String providerParamName,
    /* 可选的值级别转换代码(字典代码: reg_transform)例如 TO_EXCLUSIVE_MINUS_1D */
    String transformCode,
    /* 作为 JSON 对象的附加说明,用于平台差异/边界 */
    String notesJson,
    /* 包含性时间戳,标记此映射何时生效 */
    Instant effectiveFrom,
    /* 排他性时间戳,标记此映射何时到期;null 表示开放式 */
    Instant effectiveTo)
    implements TemporalEntity {
  /// 带验证的规范构造函数。
  ///
  /// @param id 唯一映射标识符,必须为正数
  /// @param provenanceId 来源标识符,必须为正数
  /// @param operationType 操作类型区分器,可为 null
  /// @param endpointName 此映射应用的端点名称,可为 null(null 表示所有端点)
  /// @param stdKey 标准键,不能为空白
  /// @param providerParamName 提供者参数名称,不能为空白
  /// @param transformCode 来自字典的转换代码,可为 null
  /// @param notesJson 作为 JSON 的附加说明,可为 null
  /// @param effectiveFrom 生效开始时间戳,不能为 null
  /// @param effectiveTo 生效结束时间戳,可为 null(开放式)
  /// @throws DomainValidationException 如果验证失败
  public ApiParamMapping(
      Long id,
      Long provenanceId,
      String operationType,
      String endpointName,
      String stdKey,
      String providerParamName,
      String transformCode,
      String notesJson,
      Instant effectiveFrom,
      Instant effectiveTo) {
    DomainValidationException.positive(id, "Mapping id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    String stdKeyTrimmed = DomainValidationException.notBlank(stdKey, "Standard key");
    String providerParamTrimmed =
        DomainValidationException.notBlank(providerParamName, "Provider param name");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = operationType != null ? operationType.trim() : null;
    this.endpointName = endpointName != null ? endpointName.trim() : null;
    this.stdKey = stdKeyTrimmed;
    this.providerParamName = providerParamTrimmed;
    this.transformCode = transformCode != null ? transformCode.trim() : null;
    this.notesJson = notesJson;
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
  }
}
