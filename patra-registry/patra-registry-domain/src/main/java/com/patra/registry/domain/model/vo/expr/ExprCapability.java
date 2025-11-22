package com.patra.registry.domain.model.vo.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import com.patra.registry.domain.support.TemporalEntity;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/// 表达式能力领域值对象,对应表 `reg_prov_expr_capability`。
///
/// 声明每个字段/键和范围允许的表达式操作符及约束。
///
/// @author linqibin
/// @since 0.1.0
public record ExprCapability(
    /* 主键;唯一能力配置标识符 */
    Long id,
    /* 外键,引用 {@code reg_provenance.id} */
    Long provenanceId,
    /* 操作类型区分器 (HARVEST/UPDATE/BACKFILL);{@code null} 表示应用于所有类型 */
    String operationType,
    /* 统一的内部字段键(逻辑外键到 {@code reg_expr_field_dict.field_key}) */
    String fieldKey,
    /* 包含性时间戳,标记此能力何时生效 */
    Instant effectiveFrom,
    /* 排他性时间戳,标记此能力何时到期;{@code null} 表示开放式 */
    Instant effectiveTo,
    /* 允许的操作代码 JSON 数组(例如,["TERM","IN","RANGE","EXISTS","TOKEN"]) */
    String opsJson,
    /* 允许 NOT 的操作 JSON 数组;{@code null} 表示与 {@code opsJson} 相同 */
    String negatableOpsJson,
    /* 此字段是否全局允许 NOT */
    boolean supportsNot,
    /* 允许的 TERM 匹配策略 JSON 数组(例如,["PHRASE","EXACT","ANY"]) */
    String termMatchesJson,
    /* TERM 操作是否支持区分大小写匹配 */
    boolean termCaseSensitiveAllowed,
    /* TERM 是否允许空白/空字符串值 */
    boolean termAllowBlank,
    /* TERM 值的最小长度;{@code 0} 表示无限制 */
    int termMinLength,
    /* TERM 值的最大长度;{@code 0} 表示无限制 */
    int termMaxLength,
    /* 可选的正则表达式模式,用于约束 TERM 值的字符集/格式 */
    String termPattern,
    /* IN 集合的最大元素数;{@code 0} 表示无限制 */
    int inMaxSize,
    /* IN 操作是否支持区分大小写匹配 */
    boolean inCaseSensitiveAllowed,
    /* 范围类型代码(字典代码: reg_range_kind)指示 RANGE 值类型 (NONE/DATE/DATETIME/NUMBER) */
    String rangeKindCode,
    /* RANGE 是否允许开始边界开放 (-inf, x] */
    boolean rangeAllowOpenStart,
    /* RANGE 是否允许结束边界开放 [x, +inf) */
    boolean rangeAllowOpenEnd,
    /* RANGE 是否允许在无穷处闭区间(例如,(-inf, x]) */
    boolean rangeAllowClosedAtInfinity,
    /* 最小 DATE 边界 (UTC);当 {@code rangeKindCode} 为 {@code DATE} 时适用 */
    LocalDate dateMin,
    /* 最大 DATE 边界 (UTC);当 {@code rangeKindCode} 为 {@code DATE} 时适用 */
    LocalDate dateMax,
    /* 最小 DATETIME 边界 (UTC, 微秒精度);当 {@code rangeKindCode} 为 {@code DATETIME} 时适用 */
    Instant datetimeMin,
    /* 最大 DATETIME 边界 (UTC, 微秒精度);当 {@code rangeKindCode} 为 {@code DATETIME} 时适用 */
    Instant datetimeMax,
    /* 最小 NUMBER 边界(高精度);当 {@code rangeKindCode} 为 {@code NUMBER} 时适用 */
    BigDecimal numberMin,
    /* 最大 NUMBER 边界(高精度);当 {@code rangeKindCode} 为 {@code NUMBER} 时适用 */
    BigDecimal numberMax,
    /* 此字段是否支持 EXISTS 操作符 */
    boolean existsSupported,
    /* 允许的令牌类型 JSON 数组(例如,["owner","pmcid"]) */
    String tokenKindsJson,
    /* 可选的正则表达式约束,用于令牌值 */
    String tokenValuePattern)
    implements TemporalEntity {
  public ExprCapability(
      Long id,
      Long provenanceId,
      String operationType,
      String fieldKey,
      Instant effectiveFrom,
      Instant effectiveTo,
      String opsJson,
      String negatableOpsJson,
      boolean supportsNot,
      String termMatchesJson,
      boolean termCaseSensitiveAllowed,
      boolean termAllowBlank,
      int termMinLength,
      int termMaxLength,
      String termPattern,
      int inMaxSize,
      boolean inCaseSensitiveAllowed,
      String rangeKindCode,
      boolean rangeAllowOpenStart,
      boolean rangeAllowOpenEnd,
      boolean rangeAllowClosedAtInfinity,
      LocalDate dateMin,
      LocalDate dateMax,
      Instant datetimeMin,
      Instant datetimeMax,
      BigDecimal numberMin,
      BigDecimal numberMax,
      boolean existsSupported,
      String tokenKindsJson,
      String tokenValuePattern) {
    validateRequiredFields(id, provenanceId, fieldKey, rangeKindCode, effectiveFrom);

    this.id = id;
    this.provenanceId = provenanceId;
    this.operationType = DomainValidationException.trimOrNull(operationType);
    this.fieldKey = fieldKey.trim();
    this.effectiveFrom = effectiveFrom;
    this.effectiveTo = effectiveTo;
    this.opsJson = opsJson;
    this.negatableOpsJson = negatableOpsJson;
    this.supportsNot = supportsNot;
    this.termMatchesJson = termMatchesJson;
    this.termCaseSensitiveAllowed = termCaseSensitiveAllowed;
    this.termAllowBlank = termAllowBlank;
    this.termMinLength = termMinLength;
    this.termMaxLength = termMaxLength;
    this.termPattern = DomainValidationException.trimOrNull(termPattern);
    this.inMaxSize = inMaxSize;
    this.inCaseSensitiveAllowed = inCaseSensitiveAllowed;
    this.rangeKindCode = rangeKindCode.trim();
    this.rangeAllowOpenStart = rangeAllowOpenStart;
    this.rangeAllowOpenEnd = rangeAllowOpenEnd;
    this.rangeAllowClosedAtInfinity = rangeAllowClosedAtInfinity;
    this.dateMin = dateMin;
    this.dateMax = dateMax;
    this.datetimeMin = datetimeMin;
    this.datetimeMax = datetimeMax;
    this.numberMin = numberMin;
    this.numberMax = numberMax;
    this.existsSupported = existsSupported;
    this.tokenKindsJson = tokenKindsJson;
    this.tokenValuePattern = DomainValidationException.trimOrNull(tokenValuePattern);
  }

  /// 验证能力配置的必需字段。
  ///
  /// @param id 能力标识符
  /// @param provenanceId 来源标识符
  /// @param fieldKey 字段键
  /// @param rangeKindCode 范围类型代码
  /// @param effectiveFrom 生效开始时间戳
  private static void validateRequiredFields(
      Long id, Long provenanceId, String fieldKey, String rangeKindCode, Instant effectiveFrom) {
    DomainValidationException.positive(id, "Capability id");
    DomainValidationException.positive(provenanceId, "Provenance id");
    DomainValidationException.notBlank(fieldKey, "Field key");
    DomainValidationException.notBlank(rangeKindCode, "Range kind code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
  }
}
