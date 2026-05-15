package dev.linqibin.patra.registry.api.dto.expr;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/// 表达式字段的能力元数据。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record ExprCapabilityResp(
    Long provenanceId,
    String operationType,
    String fieldKey,
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
    String tokenValuePattern,
    Instant effectiveFrom,
    Instant effectiveTo) {}
