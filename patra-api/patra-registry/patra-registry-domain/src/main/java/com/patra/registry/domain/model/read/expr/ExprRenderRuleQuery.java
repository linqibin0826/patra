package com.patra.registry.domain.model.read.expr;

import com.patra.registry.domain.exception.DomainValidationException;
import java.time.Instant;

/// 表达式渲染规则查询视图。
/// 
/// 用于查询表达式到查询语句转换规则的读优化投影。定义了如何将表达式树节点渲染为具体API查询参数。
/// 
/// @author linqibin
/// @since 0.1.0
public record ExprRenderRuleQuery(
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
    Instant effectiveTo) {
  public ExprRenderRuleQuery {
    DomainValidationException.positive(provenanceId, "Provenance id");
    fieldKey = DomainValidationException.notBlank(fieldKey, "Field key");
    opCode = DomainValidationException.notBlank(opCode, "Operation code");
    emitTypeCode = DomainValidationException.notBlank(emitTypeCode, "Emit type code");
    DomainValidationException.nonNull(effectiveFrom, "Effective from");
    operationType = operationType != null ? operationType.trim() : null;
    matchTypeCode = matchTypeCode != null ? matchTypeCode.trim() : null;
    valueTypeCode = valueTypeCode != null ? valueTypeCode.trim() : null;
  }
}
