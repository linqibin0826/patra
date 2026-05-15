package dev.linqibin.patra.registry.api.dto.expr;

import java.time.Instant;

/// 表达式输出模板的渲染规则。
///
/// 字段说明:
///
/// @author linqibin
/// @since 0.1.0
public record ExprRenderRuleResp(
    Long provenanceId,
    String operationType,
    String fieldKey,
    String opCode,
    String matchTypeCode,
    Boolean negated,
    String valueTypeCode,
    String emitTypeCode,
    String template,
    String itemTemplate,
    String joiner,
    boolean wrapGroup,
    String paramsJson,
    String functionCode,
    Instant effectiveFrom,
    Instant effectiveTo) {}
